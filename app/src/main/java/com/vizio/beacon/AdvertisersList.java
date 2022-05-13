package com.vizio.beacon;

import android.bluetooth.le.AdvertiseSettings;
import android.content.Context;
import android.util.ArrayMap;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.SwitchCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;

import java.util.Map;
import java.util.TreeMap;

public class AdvertisersList
extends RecyclerView.Adapter<AdvertisersList.ViewHolder> {
    private final String LOG_TAG = "Beacon.RecyclerAdapter";

    private final TreeMap<Long, Storage.AdvertiserConfig> configs = new TreeMap<>();
    private final TreeMap<Long, ViewHolder> items = new TreeMap<>();
    private final Context context;
    private final Listener listener;

    public interface Listener {
        void onAdvertiserToggled(Storage.AdvertiserConfig config, boolean enabled);
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        public ViewHolder(View view) {
            super(view);
            label = view.findViewById(R.id.label);
            power = view.findViewById(R.id.power);
            mode = view.findViewById(R.id.mode);
            tumbler = view.findViewById(R.id.tumbler);
            nindicator = view.findViewById(R.id.device_name_enabled_indicator);
            pindicator = view.findViewById(R.id.tx_power_level_enabled_indicator);
        }

        public TextView getLabel() { return label; }
        public TextView getPower() { return power; }
        public TextView getMode() { return mode; }
        public TextView getNindicator() { return nindicator; }
        public TextView getPindicator() { return pindicator; }
        public SwitchCompat getTumbler() { return tumbler; }

        private final TextView label;
        private final TextView power;
        private final TextView mode;
        private final TextView nindicator;
        private final TextView pindicator;
        private final SwitchCompat tumbler;
    }

    public AdvertisersList(Context context) {
        this.context = context;
        this.listener = (Listener) context;

        async(db().getAll(), result -> {
            for (Storage.AdvertiserConfig config: result) {
                configs.put(config.id, config);
            }

            notifyItemRangeInserted(0, result.size());
        });
    }

    public void add(Storage.AdvertiserConfig config) {
        async(db().insert(config), result -> {
            config.id = result;
            configs.put(result, config);
            notifyItemInserted(configs.size() - 1);
        });
    }

    public void removeNth(int position) {
        Storage.AdvertiserConfig config = getNthConfig(position);
        async(db().delete(config), result -> {
            configs.remove(config.id);
            items.remove(config.id);
            notifyItemRemoved(position);
        });
    }

    public void setTumbler(Long id, boolean value) {
        ViewHolder holder = items.get(id);
        if (holder != null) {
            holder.getTumbler().setChecked(value);
        } else {
            throw new IndexOutOfBoundsException();
        }
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater
            .from(parent.getContext())
            .inflate(R.layout.advertiser_config, parent, false);

        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Storage.AdvertiserConfig config = getNthConfig(position);
        items.put(config.id, holder);
        holder.getLabel().setText(config.label);
        holder.getPower().setText(powerLevelString(config));
        holder.getMode().setText(modeString(config));
        holder.getNindicator().setVisibility(config.includeDeviceName ? View.VISIBLE : View.GONE);
        holder.getPindicator().setVisibility(config.includeTxPowerLevel ? View.VISIBLE : View.GONE);
        holder.getTumbler().setOnCheckedChangeListener((button, isChecked) -> {
            listener.onAdvertiserToggled(config, isChecked);
        });
    }

    @Override
    public int getItemCount() {
        return configs.size();
    }

    private Storage.AdvertiserConfig getNthConfig(int position) throws IndexOutOfBoundsException {
        if (position >= configs.size())
            throw new IndexOutOfBoundsException();

        return (Storage.AdvertiserConfig)
            configs.values().toArray()[position];
    }

    private Storage.AdvertiserConfigDao db() {
        return Storage
            .getDatabase(context)
            .advertiserConfigDao();
    }

    private interface SuccessCallback<T> {
        void onSuccess(T result);
    }

    // Perform a database operation asynchronously, and run a callback on the result.
    // A failed operation gets logged and skips the callback.
    private <T> void async(ListenableFuture<T> future, SuccessCallback<T> callback) {
        Futures.addCallback(future, new FutureCallback<T>() {
            @Override
            public void onSuccess(T result)
            { callback.onSuccess(result); }

            @Override
            public void onFailure(@NonNull Throwable t)
            { Log.e(LOG_TAG, t.toString()); }
        }, ContextCompat.getMainExecutor(context));
    }

    private int powerLevelString(Storage.AdvertiserConfig config)
    { return powerLevels.get(config.power); }
    private int modeString(Storage.AdvertiserConfig config)
    { return modes.get(config.mode); }

    private static final Map<Integer, Integer> powerLevels = new ArrayMap<>();
    private static final Map<Integer, Integer> modes = new ArrayMap<>();

    static {
        powerLevels.put(AdvertiseSettings.ADVERTISE_TX_POWER_ULTRA_LOW, R.string.power_ultra_low);
        powerLevels.put(AdvertiseSettings.ADVERTISE_TX_POWER_LOW, R.string.power_low);
        powerLevels.put(AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM, R.string.power_medium);
        powerLevels.put(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH, R.string.power_high);

        modes.put(AdvertiseSettings.ADVERTISE_MODE_LOW_POWER, R.string.mode_low_power);
        modes.put(AdvertiseSettings.ADVERTISE_MODE_BALANCED, R.string.mode_balanced);
        modes.put(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY, R.string.mode_low_latency);
    }
}
