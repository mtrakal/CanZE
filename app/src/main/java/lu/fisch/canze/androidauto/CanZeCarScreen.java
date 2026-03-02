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

import androidx.annotation.NonNull;
import androidx.car.app.CarContext;
import androidx.car.app.Screen;
import androidx.car.app.model.Action;
import androidx.car.app.model.Pane;
import androidx.car.app.model.PaneTemplate;
import androidx.car.app.model.Row;
import androidx.car.app.model.Template;
import androidx.lifecycle.DefaultLifecycleObserver;
import androidx.lifecycle.LifecycleOwner;

import java.util.Locale;

import lu.fisch.canze.R;
import lu.fisch.canze.activities.MainActivity;
import lu.fisch.canze.actors.Field;
import lu.fisch.canze.actors.Fields;
import lu.fisch.canze.interfaces.FieldListener;

/**
 * Main Android Auto screen for CanZE.
 * Displays key vehicle information on the Android Auto display.
 */
public class CanZeCarScreen extends Screen implements FieldListener, AaDemoManager.DemoDataListener {

    // SID constants for monitored data
    private static final String SID_USER_SOC = "42e.0";
    private static final String SID_REAL_SOC = "654.25";
    private static final String SID_RANGE_ESTIMATE = "654.42";
    private static final String SID_DC_POWER_IN = "800.6101.24";
    private static final String SID_HV_TEMP = "42e.44";
    private static final String SID_SPEED = "5d7.0";
    private static final String SID_SOH = "658.33";

    private double userSoc = 0.0;
    private double realSoc = 0.0;
    private double rangeEstimate = 0.0;
    private double dcPowerIn = 0.0;
    private double hvTemp = 0.0;
    private double speed = 0.0;
    private double soh = 100.0;

    private boolean isRegistered = false;
    // True once at least one real field value has been received from the device
    private boolean hasRealData = false;

    public CanZeCarScreen(@NonNull CarContext carContext) {
        super(carContext);
        AaDemoManager.getInstance().addListener(this);
        getLifecycle().addObserver(new DefaultLifecycleObserver() {
            @Override
            public void onDestroy(@NonNull LifecycleOwner owner) {
                AaDemoManager.getInstance().removeListener(CanZeCarScreen.this);
                unregisterFieldListeners();
            }
        });
    }

    @NonNull
    @Override
    public Template onGetTemplate() {
        Pane.Builder paneBuilder = new Pane.Builder();

        // Check vehicle connection and demo state.
        // device != null is not enough for HTTP gateway – it is always instantiated even
        // when no address is configured. We require a non-empty address as well.
        boolean isConnected = MainActivity.device != null
                && MainActivity.fields != null
                && MainActivity.getBluetoothDeviceAddress() != null
                && !MainActivity.getBluetoothDeviceAddress().isEmpty();
        boolean isDemoActive = AaDemoManager.getInstance().isDemoActive();

        // Register field listeners as soon as a real device is available
        if (isConnected && !isRegistered) {
            registerFieldListeners();
        }

        // Show welcome screen until demo is active or real data starts arriving
        if (!isDemoActive && !hasRealData) {
            paneBuilder.addRow(new Row.Builder()
                    .setTitle(getCarContext().getString(R.string.aa_connection_status_title))
                    .addText(getCarContext().getString(R.string.aa_connection_status_disconnected))
                    .build());
            paneBuilder.addRow(new Row.Builder()
                    .setTitle(getCarContext().getString(R.string.aa_connection_instructions_title))
                    .addText(getCarContext().getString(R.string.aa_connection_instructions_text))
                    .build());
            return new PaneTemplate.Builder(paneBuilder.build())
                    .setTitle(getCarContext().getString(R.string.app_name))
                    .setHeaderAction(Action.APP_ICON)
                    .build();
        }


        // User SOC
        paneBuilder.addRow(new Row.Builder()
                .setTitle(getCarContext().getString(R.string.aa_user_soc_title))
                .addText(String.format(Locale.getDefault(), getCarContext().getString(R.string.aa_format_percent), userSoc))
                .build());

        // Real SOC
        paneBuilder.addRow(new Row.Builder()
                .setTitle(getCarContext().getString(R.string.aa_real_soc_title))
                .addText(String.format(Locale.getDefault(), getCarContext().getString(R.string.aa_format_percent), realSoc))
                .build());

        // Remaining range
        paneBuilder.addRow(new Row.Builder()
                .setTitle(getCarContext().getString(R.string.aa_range_title))
                .addText(String.format(Locale.getDefault(), getCarContext().getString(R.string.aa_format_km), rangeEstimate))
                .build());

        // Current speed
        paneBuilder.addRow(new Row.Builder()
                .setTitle(getCarContext().getString(R.string.aa_speed_title))
                .addText(String.format(Locale.getDefault(), getCarContext().getString(R.string.aa_format_kmh), speed))
                .build());

        // HV battery temperature
        paneBuilder.addRow(new Row.Builder()
                .setTitle(getCarContext().getString(R.string.aa_hv_temp_title))
                .addText(String.format(Locale.getDefault(), getCarContext().getString(R.string.aa_format_celsius), hvTemp))
                .build());

        // Charging power (only when charging)
        if (dcPowerIn > 0.1) {
            paneBuilder.addRow(new Row.Builder()
                    .setTitle(getCarContext().getString(R.string.aa_charging_power_title))
                    .addText(String.format(Locale.getDefault(), getCarContext().getString(R.string.aa_format_kw), dcPowerIn))
                    .build());
        }

        // State of Health
        paneBuilder.addRow(new Row.Builder()
                .setTitle(getCarContext().getString(R.string.aa_soh_title))
                .addText(String.format(Locale.getDefault(), getCarContext().getString(R.string.aa_format_percent), soh))
                .build());

        String title = isDemoActive
                ? getCarContext().getString(R.string.app_name) + " " + getCarContext().getString(R.string.aa_demo_badge)
                : getCarContext().getString(R.string.app_name);

        return new PaneTemplate.Builder(paneBuilder.build())
                .setTitle(title)
                .setHeaderAction(Action.APP_ICON)
                .build();
    }

    private void registerFieldListeners() {
        try {
            Fields fields = MainActivity.fields;
            if (fields != null) {
                Field field;

                field = fields.getBySID(SID_USER_SOC);
                if (field != null) field.addListener(this);

                field = fields.getBySID(SID_REAL_SOC);
                if (field != null) field.addListener(this);

                field = fields.getBySID(SID_RANGE_ESTIMATE);
                if (field != null) field.addListener(this);

                field = fields.getBySID(SID_DC_POWER_IN);
                if (field != null) field.addListener(this);

                field = fields.getBySID(SID_HV_TEMP);
                if (field != null) field.addListener(this);

                field = fields.getBySID(SID_SPEED);
                if (field != null) field.addListener(this);

                field = fields.getBySID(SID_SOH);
                if (field != null) field.addListener(this);

                isRegistered = true;
            }
        } catch (Exception e) {
            MainActivity.debug("CanZeCarScreen: Error registering listeners: " + e.getMessage());
        }
    }

    private void unregisterFieldListeners() {
        try {
            if (isRegistered && MainActivity.fields != null) {
                Fields fields = MainActivity.fields;
                Field field;

                field = fields.getBySID(SID_USER_SOC);
                if (field != null) field.removeListener(this);

                field = fields.getBySID(SID_REAL_SOC);
                if (field != null) field.removeListener(this);

                field = fields.getBySID(SID_RANGE_ESTIMATE);
                if (field != null) field.removeListener(this);

                field = fields.getBySID(SID_DC_POWER_IN);
                if (field != null) field.removeListener(this);

                field = fields.getBySID(SID_HV_TEMP);
                if (field != null) field.removeListener(this);

                field = fields.getBySID(SID_SPEED);
                if (field != null) field.removeListener(this);

                field = fields.getBySID(SID_SOH);
                if (field != null) field.removeListener(this);

                isRegistered = false;
            }
        } catch (Exception e) {
            MainActivity.debug("CanZeCarScreen: Error unregistering listeners: " + e.getMessage());
        }
    }

    // --- FieldListener ---

    @Override
    public void onFieldUpdateEvent(Field field) {
        if (field == null) return;
        String sid = field.getSID();
        double value = field.getValue();

        // Update values by SID
        switch (sid) {
            case SID_USER_SOC:
                userSoc = value;
                break;
            case SID_REAL_SOC:
                realSoc = value;
                break;
            case SID_RANGE_ESTIMATE:
                rangeEstimate = value;
                break;
            case SID_DC_POWER_IN:
                dcPowerIn = value;
                break;
            case SID_HV_TEMP:
                hvTemp = value;
                break;
            case SID_SPEED:
                speed = value;
                break;
            case SID_SOH:
                soh = value;
                break;
        }
        hasRealData = true;
        invalidate();
    }

    // --- AaDemoManager.DemoDataListener ---

    @Override
    public void onDemoDataUpdate(String sid, double value) {
        // Reuse the same value routing as real field updates
        switch (sid) {
            case AaDemoManager.SID_USER_SOC:
                userSoc = value;
                break;
            case AaDemoManager.SID_REAL_SOC:
                realSoc = value;
                break;
            case AaDemoManager.SID_RANGE_ESTIMATE:
                rangeEstimate = value;
                break;
            case AaDemoManager.SID_DC_POWER_IN:
                dcPowerIn = value;
                break;
            case AaDemoManager.SID_HV_TEMP:
                hvTemp = value;
                break;
            case AaDemoManager.SID_SPEED:
                speed = value;
                break;
            case AaDemoManager.SID_SOH:
                soh = value;
                break;
        }
        invalidate();
    }

    @Override
    public void onDemoStateChanged(boolean active) {
        if (!active) {
            // Demo stopped – clear values and return to welcome screen
            userSoc = realSoc = rangeEstimate = dcPowerIn = hvTemp = speed = 0.0;
            soh = 100.0;
            hasRealData = false;
        }
        invalidate();
    }
}
