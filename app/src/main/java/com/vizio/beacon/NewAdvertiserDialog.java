package com.vizio.beacon;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.Spinner;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;

import com.google.android.material.textfield.TextInputEditText;

public class NewAdvertiserDialog extends DialogFragment {
    public interface Listener {
        void onAdvertiserAdded(Storage.AdvertiserConfig config);
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        Activity activity = requireActivity();
        LayoutInflater inflater = activity.getLayoutInflater();
        View view = inflater.inflate(R.layout.new_advertiser_dialog, null);

        displayName = view.findViewById(R.id.display_name);
        advertisingMode = view.findViewById(R.id.advertising_mode);
        txPower = view.findViewById(R.id.tx_power);
        deviceNameCheckbox = view.findViewById(R.id.device_name_checkbox);
        txPowerLevelCheckbox = view.findViewById(R.id.tx_power_level_checkbox);

        initSpinner(activity, advertisingMode, R.array.advertising_modes);
        initSpinner(activity, txPower, R.array.tx_power_levels);

        return new AlertDialog.Builder(requireActivity())
            .setView(view)
            .setPositiveButton(R.string.add, this::commit)
            .setNegativeButton(R.string.cancel, this::reject)
            .create();
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        listener = (Listener) context;
    }

    private void commit(DialogInterface dialogInterface, int which) {
        Storage.AdvertiserConfig config = new Storage.AdvertiserConfig();
        config.label = displayName.getText().toString();
        config.power = txPower.getSelectedItemPosition();
        config.mode = advertisingMode.getSelectedItemPosition();
        config.includeDeviceName = deviceNameCheckbox.isChecked();
        config.includeTxPowerLevel = txPowerLevelCheckbox.isChecked();
        listener.onAdvertiserAdded(config);
    }

    private void reject(DialogInterface dialogInterface, int which) {
    }

    private void initSpinner(Context context, Spinner spinner, int resource) {
        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(
            context, resource,
            android.R.layout.simple_spinner_item);

        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
    }

    private TextInputEditText displayName;
    private Spinner advertisingMode;
    private Spinner txPower;
    private CheckBox deviceNameCheckbox;
    private CheckBox txPowerLevelCheckbox;
    private Listener listener;
}
