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

package lu.fisch.canze.androidauto;

import android.os.Handler;
import android.os.Looper;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Manages Android Auto demo mode.
 * Sends fake vehicle data to registered listeners when real device is not connected.
 */
public class AaDemoManager {

    public interface DemoDataListener {
        void onDemoDataUpdate(String sid, double value);
        void onDemoStateChanged(boolean active);
    }

    // SID constants matching CanZeCarScreen
    static final String SID_USER_SOC       = "42e.0";
    static final String SID_REAL_SOC       = "654.25";
    static final String SID_RANGE_ESTIMATE = "654.42";
    static final String SID_DC_POWER_IN    = "800.6101.24";
    static final String SID_HV_TEMP        = "42e.44";
    static final String SID_SPEED          = "5d7.0";
    static final String SID_SOH            = "658.33";

    private static final long UPDATE_INTERVAL_MS = 1500;

    private static AaDemoManager instance;

    private boolean demoActive = false;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Random random = new Random();
    private final List<DemoDataListener> listeners = new ArrayList<>();

    // Animated demo values – reset on each startDemo()
    private double userSoc       = 72.0;
    private double realSoc       = 68.5;
    private double rangeEstimate = 180.0;
    private double speed         = 0.0;
    private double hvTemp        = 24.5;
    private double dcPowerIn     = 0.0;
    private double soh           = 94.2;

    private final Runnable tickRunnable = new Runnable() {
        @Override
        public void run() {
            if (!demoActive) return;
            tickDemoData();
            handler.postDelayed(this, UPDATE_INTERVAL_MS);
        }
    };

    private AaDemoManager() {}

    public static AaDemoManager getInstance() {
        if (instance == null) {
            instance = new AaDemoManager();
        }
        return instance;
    }

    public boolean isDemoActive() {
        return demoActive;
    }

    public void addListener(DemoDataListener listener) {
        if (!listeners.contains(listener)) {
            listeners.add(listener);
        }
    }

    public void removeListener(DemoDataListener listener) {
        listeners.remove(listener);
    }

    /** Starts demo mode. Does nothing if already active. */
    public void startDemo() {
        if (demoActive) return;

        // Reset values to realistic starting point
        userSoc       = 72.0;
        realSoc       = 68.5;
        rangeEstimate = 180.0;
        speed         = 0.0;
        hvTemp        = 24.5;
        dcPowerIn     = 0.0;
        soh           = 94.2;

        demoActive = true;
        notifyStateChanged(true);

        // Start first tick after one interval so the welcome→data transition is visible
        handler.postDelayed(tickRunnable, UPDATE_INTERVAL_MS);
    }

    /** Stops demo mode. Does nothing if not active. */
    public void stopDemo() {
        if (!demoActive) return;
        demoActive = false;
        handler.removeCallbacks(tickRunnable);
        notifyStateChanged(false);
    }

    /** Toggles demo mode. Returns new state. */
    public boolean toggleDemo() {
        if (demoActive) {
            stopDemo();
        } else {
            startDemo();
        }
        return demoActive;
    }

    // Animates demo values slightly each tick and notifies listeners
    private void tickDemoData() {
        // Simulate gentle driving: speed oscillates 0–120 km/h
        speed = clamp(speed + (random.nextDouble() * 10 - 4), 0, 120);

        // SOC slowly decreases while driving, recovers if charging
        if (dcPowerIn > 0.1) {
            userSoc = clamp(userSoc + 0.3, 0, 100);
            realSoc = clamp(realSoc + 0.25, 0, 100);
        } else {
            userSoc = clamp(userSoc - (speed > 5 ? 0.05 : 0), 0, 100);
            realSoc = clamp(realSoc - (speed > 5 ? 0.04 : 0), 0, 100);
        }

        // Range follows SOC roughly
        rangeEstimate = realSoc * 2.8;

        // Temperature oscillates around 24°C
        hvTemp = clamp(hvTemp + (random.nextDouble() * 0.4 - 0.2), 15, 45);

        // Occasionally simulate charging (only when slow/stopped)
        if (speed < 5 && random.nextInt(20) == 0) {
            dcPowerIn = dcPowerIn > 0 ? 0 : 22.0 + random.nextDouble() * 28;
        }

        notifyData(SID_USER_SOC,       userSoc);
        notifyData(SID_REAL_SOC,       realSoc);
        notifyData(SID_RANGE_ESTIMATE, rangeEstimate);
        notifyData(SID_SPEED,          speed);
        notifyData(SID_HV_TEMP,        hvTemp);
        notifyData(SID_DC_POWER_IN,    dcPowerIn);
        notifyData(SID_SOH,            soh);
    }

    private void notifyData(String sid, double value) {
        for (DemoDataListener l : new ArrayList<>(listeners)) {
            l.onDemoDataUpdate(sid, value);
        }
    }

    private void notifyStateChanged(boolean active) {
        for (DemoDataListener l : new ArrayList<>(listeners)) {
            l.onDemoStateChanged(active);
        }
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
