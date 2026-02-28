/*
    CanZE
    Take a closer look at your ZE car

    Copyright (C) 2015 - The CanZE Team
    http://canze.fisch.lu

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or any
    later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program.  If not, see <http://www.gnu.org/licenses/>.
*/

package lu.fisch.canze.devices;

import android.content.Context;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;

import com.hoho.android.usbserial.driver.UsbSerialPort;
import com.hoho.android.usbserial.util.SerialInputOutputManager;

import java.io.IOException;
import java.util.Calendar;

import lu.fisch.canze.R;
import lu.fisch.canze.activities.MainActivity;
import lu.fisch.canze.activities.SettingsActivity;
import lu.fisch.canze.actors.Frame;
import lu.fisch.canze.actors.Message;
import lu.fisch.canze.interfaces.BluetoothEvent;

/**
 * USB driver for ELM327 compatible devices connected via USB serial port
 * Based on ELM327 Bluetooth implementation
 */
public class USB extends Device {

    private static final String ACTION_USB_PERMISSION = "lu.fisch.canze.USB_PERMISSION";

    private static final int BAUD_RATE = 38400; // Standard USB-serial baud rate for ELM327
    private static final int DATA_BITS = 8;
    private static final int STOP_BITS = UsbSerialPort.STOPBITS_1;
    private static final int PARITY = UsbSerialPort.PARITY_NONE;

    private static final int DEFAULT_TIMEOUT = 500;
    private static final int MINIMUM_TIMEOUT = 100;
    private int generalTimeout = 500;

    private static final char EOM1 = '\r';
    private static final char EOM2 = '>';
    private static final char EOM3 = '?';

    private boolean deviceIsInitialized = false;
    private int lastId = 0;
    private boolean lastCommandWasFreeFrame = false;

    private UsbSerialPort usbPort;
    private SerialInputOutputManager ioManager;
    private UsbDeviceConnection connection;
    private final StringBuilder readBuffer = new StringBuilder();
    private final Object readLock = new Object();

    // Event listener for connection state changes (similar to BluetoothEvent)
    private static BluetoothEvent connectionEventListener = null;

    /**
     * Set event listener for USB connection state changes
     * @param listener BluetoothEvent listener (despite the name, it works for USB too)
     */
    public static void setConnectionEventListener(BluetoothEvent listener) {
        connectionEventListener = listener;
    }

    protected boolean initDevice(int toughness, int retries) {
        if (initDevice(toughness)) return true;
        while (retries-- > 0) {
            MainActivity.debug("USB: flushWithTimeout");
            flushWithTimeout(500);
            MainActivity.debug("USB: initDevice(" + toughness + "), " + retries + " retries left");
            if (initDevice(toughness)) return true;
        }
        MainActivity.toast(MainActivity.TOAST_ELM, R.string.message_HardResetFailed);
        MainActivity.debug("USB: Hard reset failed");

        ///----- WE ARE HERE INSIDE THE POLLER THREAD, SO
        ///----- JOINING CAN'T WORK!

        // ... but we don't want the next request to happen,
        // so we need to stop the poller here anyway, but
        // DO NOT JOIN IT!
        setPollerActive(false);

        (new Thread(new Runnable() {
            @Override
            public void run() {
                MainActivity.debug("USB: stopUSB (via MainActivity)");
                MainActivity.getInstance().stopBluetooth(false);
//                stopAndJoin();
                MainActivity.debug("USB: reloadUSB (via MainActivity)");
                MainActivity.getInstance().reloadBluetooth(false);
            }
        })).start();

        return false;
    }

    public boolean initDevice(int toughness) {
        MainActivity.debug("USB: initDevice (" + toughness + ")");

        String response;
        int elmVersion = 0;

        lastInitProblem = "";

        // ensure the dongle header field is set again
        lastId = 0;

        // extremely soft, just clear the global error condition
        if (toughness == TOUGHNESS_NONE) {
            deviceIsInitialized = true;
            return deviceIsInitialized;
        }

        // Connect to USB device if not already connected
        if (!isConnected()) {
            if (!connect()) {
                lastInitProblem = "Failed to connect to USB device";
                MainActivity.toast(MainActivity.TOAST_ELM, lastInitProblem);
                return false;
            }
        }

        killCurrentOperation();

        if (toughness == TOUGHNESS_HARD || toughness == TOUGHNESS_MEDIUM) {
            response = sendAndWaitForAnswer("atz", 2000, true, -1, true);
            if (!response.contains("ELM")) {
                response = sendAndWaitForAnswer("atws", 500, true, -1, true);
            }
        } else {
            response = sendAndWaitForAnswer("atd", 500, true, -1, true);
        }

        MainActivity.debug("USB: version: [" + response + "]");

        response = response.trim();
        if (response.equals("")) {
            lastInitProblem = "ELM is not responding (toughness = " + toughness + ")";
            MainActivity.toast(MainActivity.TOAST_ELM, lastInitProblem);
            return false;
        }

        // only do version control at a full reset
        if (toughness <= TOUGHNESS_MEDIUM) {
            if (response.toUpperCase().contains("V1.3")) {
                elmVersion = 13;
            } else if (response.toUpperCase().contains("V1.4")) {
                elmVersion = 14;
            } else if (response.toUpperCase().contains("V1.5")) {
                elmVersion = 15;
            } else if (response.toUpperCase().contains("V2.")) {
                elmVersion = 20;
            } else if (response.toUpperCase().contains("INNOCAR")) {
                elmVersion = 8015;
            } else {
                lastInitProblem = "Unrecognized ELM version response [" + response.replace("\r", "<cr>").replace(" ", "<sp>") + "]";
                MainActivity.toast(MainActivity.TOAST_ELM, lastInitProblem);
                return false;
            }
        }


        deviceIsInitialized = false;


        // ***** CanZE version *******************************************************************
        // ate0         (no echo. At this point, echo is still on (except when atd was issued), so
        //              we still need to absorb the echoed command. After this point, echo is
        //              finally off so we can safely check for OK messages. If the app starts
        //              responding with toasts showing the responses in brackets equal to the
        //              commands, somehow the echo was not executed. so maybe we need to check for
        //              that specific condition in the next command.)
        // ats0         (no spaces)
        // atsp6        (CAN 500K 11 bit)
        // atat1        (auto timing)
        // atcaf0       (no formatting)
        // atfcsh77b    (flow control response ID to 77b. This is needed to be able to set the flow
        //               control response. Any ID would be fine
        // atfcsd300010 (the flow control response data to 300010 (flow control, clear to send,
        //               all frames, 16 ms wait between frames. Note that it is not possible to let
        //               the ELM request each frame as the Altered Flow Control only responds to a
        //               FRST, not a NEXT)
        // atfcsm1 (flow control mode 1: ID and data suplied)

        //String[] commands = "ate0;ats0;atsp6;atat1;atcaf0;atfcsh77b;atfcsd300010;atfcsm1".split(";");

        // ***** DDT4ALL version *****************************************************************
        // ate1         (echo on) ==> we don't do this
        // ats0         (no spaces)
        // ath0         (headers off = default)
        // atl0         (Linefeeds off)
        // atal         (allow long messages)
        // atcaf0       (no formatting)
        //              Here they stop initialization and do the remainder per ECU.
        //              We do ATSH and ATFCSH too per request (since we constantly switch ECU's,
        //              (we can optimize that, remember last ECU), however they also do flow control
        //              initialisation and bus speed, per ECU, which we prefer to do here.
        //              We also skip ATCRA (see comments at freeframe)
        // atfcsh77b    (flow control response ID to 77b. This is needed to be able to set the flow
        //               control response. Any ID would be fine
        // atfcsd300000 (the flow control response data to 300010 (flow control, clear to send,
        //               all frames, no wait between frames. Note that it is not possible to let
        //               the ELM request each frame as the Altered Flow Control only responds to a
        //               FRST, not a NEXT)
        // atsp6        (CAN 500K 11 bit) ==> might need to change that if we want to support
        //               pin re-assignment to ie the MM bus

        String[] commands = "ate0;ats0;ath0;atl0;atal;atcaf0;atfcsh77b;atfcsd300000;atfcsm1;atsp6".split(";");

        boolean first = true;
        for (String command : commands) {
            if (!initCommandExpectOk(command, first)) {
                lastInitProblem = command + " command problem";
                return deviceIsInitialized;
            }
            first = false;
        }

        if (toughness == TOUGHNESS_HARD) {
            switch (elmVersion) {
                case 13:
                    MainActivity.toast(MainActivity.TOAST_ELM, R.string.message_ELM13Ready);
                    break;
                case 14:
                    MainActivity.toast(MainActivity.TOAST_ELM, R.string.message_ELM13Ready);
                    break;
                case 15:
                    MainActivity.toast(MainActivity.TOAST_ELM, R.string.message_ELMReady);
                    break;
                case 20:
                    lastInitProblem = MainActivity.getStringSingle(R.string.message_ELM20Ready);
                    MainActivity.toast(MainActivity.TOAST_ELM, lastInitProblem);
                    break;
                case 8015:
                    MainActivity.toast(MainActivity.TOAST_ELM, R.string.message_ELM8015Ready);
                    break;

                // default should never be reached!!
                default:
                    lastInitProblem = MainActivity.getStringSingle(R.string.message_ELMUnknown);
                    MainActivity.toast(MainActivity.TOAST_ELM, lastInitProblem);
                    break;
            }
        }

        deviceIsInitialized = true;
        return deviceIsInitialized;
    }

    /**
     * Connect to USB device. This should be called before initDevice().
     * Reads USB device info from SharedPreferences and finds the matching port.
     * @return true if connection was successful
     */
    public boolean connect() {
        try {
            // Notify listener that we are about to connect
            if (connectionEventListener != null) {
                connectionEventListener.onBeforeConnect();
            }

            // Load USB device info from SharedPreferences
            android.content.SharedPreferences settings = MainActivity.getInstance()
                .getSharedPreferences(MainActivity.PREFERENCES_FILE, Context.MODE_PRIVATE);
            String deviceAddress = settings.getString(SettingsActivity.SETTING_DEVICE_ADDRESS, null);

            if (deviceAddress == null || !deviceAddress.startsWith("USB:")) {
                MainActivity.debug("USB: No USB device configured in settings");
                return false;
            }

            // Parse device ID and port number from saved address (format: "USB:deviceId:portNumber")
            String[] parts = deviceAddress.split(":");
            if (parts.length < 3) {
                MainActivity.debug("USB: Invalid USB device address format: " + deviceAddress);
                return false;
            }

            int savedDeviceId = Integer.parseInt(parts[1]);
            int savedPortNumber = Integer.parseInt(parts[2]);

            // Find the USB device and port
            UsbManager usbManager = (UsbManager) MainActivity.getInstance().getSystemService(Context.USB_SERVICE);
            if (usbManager == null) {
                MainActivity.debug("USB: UsbManager not available");
                return false;
            }

            com.hoho.android.usbserial.driver.UsbSerialProber prober =
                com.hoho.android.usbserial.driver.UsbSerialProber.getDefaultProber();
            java.util.List<com.hoho.android.usbserial.driver.UsbSerialDriver> availableDrivers =
                prober.findAllDrivers(usbManager);

            if (availableDrivers.isEmpty()) {
                MainActivity.debug("USB: No USB devices found");
                return false;
            }

            // Find matching device and port
            UsbSerialPort selectedPort = null;
            for (com.hoho.android.usbserial.driver.UsbSerialDriver driver : availableDrivers) {
                if (driver.getDevice().getDeviceId() == savedDeviceId) {
                    java.util.List<UsbSerialPort> ports = driver.getPorts();
                    for (UsbSerialPort port : ports) {
                        if (port.getPortNumber() == savedPortNumber) {
                            selectedPort = port;
                            break;
                        }
                    }
                    if (selectedPort != null) break;
                }
            }

            if (selectedPort == null) {
                MainActivity.debug("USB: Configured USB device not found (deviceId=" + savedDeviceId + ", port=" + savedPortNumber + ")");
                return false;
            }

            usbPort = selectedPort;

            // Check if we have permission to access the device
            android.hardware.usb.UsbDevice usbDevice = usbPort.getDriver().getDevice();
            if (!usbManager.hasPermission(usbDevice)) {
                MainActivity.debug("USB: No permission for device, requesting permission...");

                // Request permission
                android.app.PendingIntent permissionIntent = android.app.PendingIntent.getBroadcast(
                    MainActivity.getInstance(),
                    0,
                    new android.content.Intent(ACTION_USB_PERMISSION),
                    android.app.PendingIntent.FLAG_IMMUTABLE
                );
                usbManager.requestPermission(usbDevice, permissionIntent);

                // Wait for permission (with timeout)
                int timeout = 5000; // 5 seconds
                int waited = 0;
                while (!usbManager.hasPermission(usbDevice) && waited < timeout) {
                    try {
                        Thread.sleep(100);
                        waited += 100;
                    } catch (InterruptedException e) {
                        // ignore
                    }
                }

                if (!usbManager.hasPermission(usbDevice)) {
                    MainActivity.debug("USB: Permission not granted (timeout or denied)");
                    MainActivity.toast(MainActivity.TOAST_NONE, "USB permission required. Please grant permission and try again.");
                    return false;
                }

                MainActivity.debug("USB: Permission granted");
            }

            connection = usbManager.openDevice(usbDevice);

            if (connection == null) {
                MainActivity.debug("USB: Failed to open device connection even with permission");
                return false;
            }

            usbPort.open(connection);
            usbPort.setParameters(BAUD_RATE, DATA_BITS, STOP_BITS, PARITY);

            // Start IO manager for reading
            ioManager = new SerialInputOutputManager(usbPort, new SerialInputOutputManager.Listener() {
                @Override
                public void onNewData(byte[] data) {
                    synchronized (readLock) {
                        for (byte b : data) {
                            readBuffer.append((char) b);
                        }
                        readLock.notifyAll();
                    }
                }

                @Override
                public void onRunError(Exception e) {
                    MainActivity.debug("USB: IO error: " + e.getMessage());
                }
            });

            ioManager.start();

            MainActivity.debug("USB: Connected to device (deviceId=" + savedDeviceId + ", port=" + savedPortNumber + ")");

            // Notify listener that we are now connected
            if (connectionEventListener != null) {
                connectionEventListener.onAfterConnect(null); // null for BluetoothSocket, not needed for USB
            }

            return true;
        } catch (Exception e) {
            MainActivity.debug("USB: Connection error: " + e.getMessage());
            return false;
        }
    }

    /**
     * Disconnect from USB device.
     */
    public void disconnect() {
        disconnectUsbDevice();
    }

    /**
     * Check if USB device is connected.
     * @return true if connected
     */
    public boolean isConnected() {
        return usbPort != null && connection != null;
    }

    private void killCurrentOperation() {
        // ensure any running operation is stopped
        // sending a return might restart the last command. Bad plan.
        sendNoWait("x");
        // discard everything that still comes in
        flushWithTimeoutCore(200, '\0');
        // if a command was running, it is interrupted now and the ELM is waiting for a command.
        // However, if there was no command running, the x in the buffer will screw up the next
        // command. There are two possibilities: Sending a Backspace and hope for the best, or
        // sending x <CR> and being sure the ELM will report an unknown command (prompt a ? mark),
        // as it will be processing either x <CR> or xx <CR>. We choose the latter and discard
        // the ? anser
        sendNoWait("x\r");
        if (!flushWithTimeoutCore(500, '\0')) {
            MainActivity.debug("USB: KillCurrentOperation unable to flush after x");
        }
    }

    private void flushWithTimeout(int timeout) {
        flushWithTimeout(timeout, '\0');
    }

    private void flushWithTimeout(int timeout, char eom) {
        if (flushWithTimeoutCore(timeout, eom)) return;
        killCurrentOperation();
    }

    private boolean flushWithTimeoutCore(int timeout, char eom) {
        // empty incoming buffer
        // just make sure there is no previous response
        // the ELM might be in a mode where it is spewing out data, and that might put this
        // method in an endless loop. If there are more than 100 character flushed, return false
        // this should normally be followed by a failure and thus device re-initialisation

        int count = 100;

        try {
            // fast track, don't use expensive calendar.....
            if (timeout == 0) {
                synchronized (readLock) {
                    while (readBuffer.length() > 0 && count-- > 0) {
                        readBuffer.setLength(0);
                    }
                }
            } else {
                long end = Calendar.getInstance().getTimeInMillis() + timeout;
                while (Calendar.getInstance().getTimeInMillis() < end) {
                    synchronized (readLock) {
                        if (readBuffer.length() > 0) {
                            for (int i = 0; i < readBuffer.length(); i++) {
                                if (readBuffer.charAt(i) == eom) {
                                    readBuffer.setLength(0);
                                    return true;
                                }
                                if (count-- == 0) {
                                    readBuffer.setLength(0);
                                    return false;
                                }
                            }
                            readBuffer.setLength(0);
                            end = Calendar.getInstance().getTimeInMillis() + timeout;
                        } else {
                            Thread.sleep(5);
                        }
                    }
                }
            }
        } catch (InterruptedException e) {
            // ignore
        }
        return true;
    }

    private boolean initCommandExpectOk(String command) {
        return initCommandExpectOk(command, false, true);
    }

    private boolean initCommandExpectOk(String command, boolean untilEmpty) {
        return initCommandExpectOk(command, untilEmpty, true);
    }

    private boolean initCommandExpectOk(String command, boolean untilEmpty, boolean addReturn) {
        String response = "";
        for (int i = 2; i > 0; i--) {
            if (untilEmpty) {
                response = sendAndWaitForAnswer(command, 40, true, -1, addReturn); // wait 40 ms for untilempty
            } else {
                response = sendAndWaitForAnswer(command, 0, false, -1, addReturn); // // just one line
            }
            if (response.toUpperCase().contains("OK")) return true; // we're done if we got an OK
            if (MainActivity.altFieldsMode) untilEmpty = true; // crappy dongles answer with things like CR > LF [space]
        }

        // we've tried and tried and failed here
        /* if (timeoutLogLevel >= 2 || (timeoutLogLevel >= 1 && !command.startsWith("atma") && command.startsWith("at"))) {
            MainActivity.toast("Err " + command + " [" + response.replace("\r", "<cr>").replace(" ", "<sp>") + "]");
        } */
        MainActivity.toast(MainActivity.TOAST_ELM, "Error [" + command + "] [" + response.replace("\r", "<cr>").replace(" ", "<sp>") + "]");
        MainActivity.debug("USB.initCommandExpectOk c:" + command + ", untilempty:" + untilEmpty + " res:" + response);

        return false;
    }

    private void sendNoWait(String command) {
        if (!isConnected()) return;
        if (command != null) {
            try {
                usbPort.write(command.getBytes(), 1000);
            } catch (IOException e) {
                MainActivity.debug("USB: Write error: " + e.getMessage());
            }
        }
    }

    private String sendAndWaitForAnswer(String command, int waitMillis) {
        return sendAndWaitForAnswer(command, waitMillis, false, -1, true);
    }

    private String sendAndWaitForAnswer(String command, int waitMillis, int answerLinesCount) {
        return sendAndWaitForAnswer(command, waitMillis, false, answerLinesCount, true);
    }

    private String sendAndWaitForAnswer(String command, int waitMillis, boolean untilEmpty) {
        return sendAndWaitForAnswer(command, waitMillis, untilEmpty, -1, true);
    }

    private String sendAndWaitForAnswer(String command, int waitMillis, boolean untilEmpty, int answerLinesCount, boolean addReturn) {

        int maxUntilEmptyCounter = 10;
        int maxLengthCounter = 500; // char = nibble, so 2000 bits

        if (!isConnected()) return "";

        if (command != null) {
            flushWithTimeout(10, '>');
            try {
                // send the command
                usbPort.write((command + (addReturn ? "\r" : "")).getBytes(), 1000);
            } catch (IOException e) {
                MainActivity.debug("USB: Write error: " + e.getMessage());
                return "";
            }
        }

        MainActivity.debug("Send > " + command);

        // init the buffer
        boolean stop = false;
        StringBuilder localBuffer = new StringBuilder();
        // wait for answer
        long end = Calendar.getInstance().getTimeInMillis() + generalTimeout;
        boolean timedOut = false;

        while (!stop && !timedOut) {
            try {
                synchronized (readLock) {
                    if (readBuffer.length() > 0) {
                        int data = readBuffer.charAt(0);
                        readBuffer.deleteCharAt(0);

                        if (data != -1) {
                            char ch = (char) data;
                            if (ch == '\n') ch = '\r';
                            localBuffer.append(ch);

                            if (ch == EOM1 || ch == EOM2 || ch == EOM3) {
                                answerLinesCount--;
                                if (!untilEmpty) {
                                    if (answerLinesCount <= 0) {
                                        stop = true;
                                    } else {
                                        end = Calendar.getInstance().getTimeInMillis() + generalTimeout;
                                    }
                                } else {
                                    stop = (readBuffer.length() == 0);
                                    if (stop) {
                                        try {
                                            readLock.wait(waitMillis > 50 ? 400 : 50);
                                            end = Calendar.getInstance().getTimeInMillis() + generalTimeout;
                                        } catch (InterruptedException e) {
                                            // do nothing
                                        }
                                        stop = (readBuffer.length() == 0);
                                    } else {
                                        if (--maxUntilEmptyCounter <= 0)
                                            timedOut = true;
                                    }
                                }
                            } else {
                                if (--maxLengthCounter <= 0)
                                    timedOut = true;
                            }
                        }
                    } else {
                        try {
                            readLock.wait(10);
                        } catch (InterruptedException e) {
                            // do nothing
                        }
                    }
                }

                if (Calendar.getInstance().getTimeInMillis() > end) {
                    timedOut = true;
                }

            } catch (Exception e) {
                MainActivity.debug("USB: Read error: " + e.getMessage());
            }
        }

        // set the flag that a timeout has occurred. someThingWrong can be inspected anywhere, but we reset the device after a full filter has been run
        if (timedOut) {
            MainActivity.toast(MainActivity.TOAST_ELM, "Timeout on [" + command + "] [" + localBuffer.toString().replace("\r", "<cr>").replace(" ", "<sp>") + "]");
            MainActivity.debug("USB: sendAndWaitForAnswer > timed out on [" + command + "] [" + localBuffer.toString().replace("\r", "<cr>").replace(" ", "<sp>") + "]");
            return "";
        }

        MainActivity.debug("Recv < " + localBuffer);
        return localBuffer.toString();
    }

    @Override
    public void clearFields() {
        super.clearFields();
    }

    @Override
    public Message requestFreeFrame(Frame frame) {
        if (!deviceIsInitialized) {
            return new Message(frame, "-E-Re-initialisation needed", true);
        }

        String hexData;

        // ensure the ATCRA filter is reset in the next NON free frame request
        lastCommandWasFreeFrame = true;

        // EML needs the filter to be 3 hex symbols and contains the from CAN id of the ECU.
        // getFromIdHex returns 3 chars for 11 bit id, and 8 bits for a 29 bit id
        String emlFilter = frame.getFromIdHex();

        MainActivity.debug("USB: requestFreeFrame: atcra" + emlFilter);
        if (!initCommandExpectOk("atcra" + emlFilter))
            return new Message(frame, "-E-Problem sending atcra command", true);

        generalTimeout = (int) (frame.getInterval() * 1.3 + 50);
        if (generalTimeout < MINIMUM_TIMEOUT) generalTimeout = MINIMUM_TIMEOUT;
        MainActivity.debug("USB: requestFreeFrame > TIMEOUT = " + generalTimeout);

        // 10 ms plus repeat time timeout, do not wait until empty, do not count lines, add \r to command
        hexData = sendAndWaitForAnswer("atma", frame.getInterval() + 10);

        MainActivity.debug("USB: requestFreeFrame > hexData = [" + hexData + "]");

        // the dongle starts babbling now. sendAndWaitForAnswer should stop at the first full line
        // ensure any running operation is stopped
        // sending a return might restart the last command. Bad plan.
        sendNoWait("x");
        // let it settle down, the ELM should indicate STOPPED then prompt >
        flushWithTimeout(100, '>');
        generalTimeout = DEFAULT_TIMEOUT;

        // atar     (clear filter)
        // AM has suggested the atar might not be neccesary as it might only influence cra filters and they are always set
        // however, make sure proper flushing is done
        // if cra does influence ISO-TP requests, an small optimization might be to only sending an atar when switching from free
        // frames to isotp frames.
        // if (!initCommandExpectOk("atar")) someThingWrong |= true;

        hexData = hexData.trim();
        if (hexData.equals(""))
            return new Message(frame, "-E-data empty", true);
        else
            return new Message(frame, hexData, false);
    }

    @Override
    public Message requestIsoTpFrame(Frame frame) {
        if (!deviceIsInitialized) {
            return new Message(frame, "-E-Re-initialisation needed", true);
        }

        String hexData;
        int len;

        // PERFORMANCE ENHANCEMENT: only send ATAR if coming from a free frame
        if (lastCommandWasFreeFrame) {
            // atar     (clear filter set by free frame capture method)
            if (!initCommandExpectOk("atar")) {
                return new Message(frame, "-E-Problem sending atar command", true);
            }
            lastCommandWasFreeFrame = false;
        }

        // PERFORMANCE ENHANCEMENT II: lastId contains the CAN id of the previous ISO-TP command. If
        // the current ID is the same, no need to re-address that ECU. Even if different ECU but
        // same id length, no change 11/29 bit mode needed either.
        // (re)enabled after 1.54 release
        // lastId = 0;
        if (lastId != frame.getFromId()) {
            // IIa: 11/29 bit mode change only if needed
            if (frame.isExtended()) {
                if (lastId < 0x1000) {
                    // switch to 29 bit
                    if (!initCommandExpectOk("atsp7",true))
                        return new Message(frame, "-E-Problem sending atsp7 command", true);
                    // set prio using AT CP
                    if (!initCommandExpectOk("atcp" + frame.getToIdHexMSB(), true))
                        return new Message(frame, "-E-Problem sending atcp command", true);
                }
            } else {
                if (lastId >= 0x1000 || lastId == 0) { // 0 check if optimization II is disabled
                    // switch to 11 bit
                    if (!initCommandExpectOk("atsp6", true))
                        return new Message(frame, "-E-Problem sending atsp6 command", true);
                }
            }

            // change ECU address
            // Set header
            if (!initCommandExpectOk("atsh" + frame.getToIdHexLSB()))
                return new Message(frame, "-E-Problem sending atsh command", true);
            // Set filter
            if (!initCommandExpectOk("atcra" + frame.getFromIdHex()))
                return new Message(frame, "-E-Problem sending atcra command", true);
            // Set flow control response ID
            if (!initCommandExpectOk("atfcsh" + frame.getToIdHex()))
                return new Message(frame, "-E-Problem sending atfcsh command", true);

            lastId = frame.getFromId();
        }

        // ISOTP outgoing starts here
        int outgoingLength = frame.getRequestId().length();
        String command = frame.getRequestId();

        if (outgoingLength <= 14) {
            hexData = sendAndWaitForAnswer(command, 0);
        } else {
            hexData = sendAndWaitForAnswer(command, 0, true);
        }

        hexData = hexData.trim();
        if (hexData.equals(""))
            return new Message(frame, "-E-data empty", true);
        else
            return new Message(frame, hexData, false);
    }

    @Override
    public void stopAndJoin() {
        super.stopAndJoin();
        disconnectUsbDevice();
    }

    private void disconnectUsbDevice() {
        try {
            if (ioManager != null) {
                ioManager.stop();
                ioManager = null;
            }
            if (usbPort != null) {
                usbPort.close();
                usbPort = null;
            }
            if (connection != null) {
                connection.close();
                connection = null;
            }
            MainActivity.debug("USB: Disconnected");

            // Notify listener that we are now disconnected
            if (connectionEventListener != null) {
                connectionEventListener.onAfterDisconnect();
            }
        } catch (IOException e) {
            MainActivity.debug("USB: Disconnect error: " + e.getMessage());
        }
    }
}
