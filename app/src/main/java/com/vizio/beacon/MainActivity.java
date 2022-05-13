package com.vizio.beacon;

// There is a lot of UI-related noise in the file - if you want to get
// straight to some Bluetooth action, start with the startAdvertising() and
// stopAdvertising() functions.

import android.Manifest;
import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.le.AdvertiseCallback;
import android.bluetooth.le.AdvertiseData;
import android.bluetooth.le.AdvertiseSettings;
import android.bluetooth.le.BluetoothLeAdvertiser;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.util.ArrayDeque;
import java.util.Map;
import java.util.TreeMap;

public class MainActivity
extends AppCompatActivity
implements NewAdvertiserDialog.Listener, AdvertisersList.Listener {
    private static final int ADVERTISE_PERMISSION = 0;
    private static final int CONNECT_PERMISSION = 1;
    private static final int ENABLE_BLUETOOTH_REQUEST = 2;

    private final String LOG_TAG = "Beacon";
    private final Map<Integer, ArrayDeque<Continuation>> pending = new TreeMap<>();
    private final Map<Long, AdvertiseCallback> advertiseCallbacks = new TreeMap<>();
    private AdvertisersList advertisersList;

    private interface Continuation {
        void onSuccess();
        void onFailure();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTitle(R.string.app_name);
        setContentView(R.layout.activity_main);

        Toolbar toolbar = findViewById(R.id.main_toolbar);
        setSupportActionBar(toolbar);

        FloatingActionButton fab = findViewById(R.id.fab);
        fab.setOnClickListener(view -> {
            NewAdvertiserDialog dialog = new NewAdvertiserDialog();
            dialog.show(getSupportFragmentManager(), "NewAdvertiserDialog");
        });

        RecyclerView recyclerView = findViewById(R.id.recycler);
        recyclerView.setLayoutManager(new LinearLayoutManager(MainActivity.this));
        advertisersList = new AdvertisersList(MainActivity.this);
        recyclerView.setAdapter(advertisersList);

        new ItemTouchHelper(new ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.RIGHT) {
            @Override
            public boolean onMove(
                @NonNull RecyclerView recyclerView,
                @NonNull RecyclerView.ViewHolder viewHolder,
                @NonNull RecyclerView.ViewHolder target)
            { return false; }

            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
                int pos = viewHolder.getAdapterPosition();
                advertisersList.removeNth(pos);
            }
        }).attachToRecyclerView(recyclerView);
    }

    @Override
    public void onAdvertiserAdded(Storage.AdvertiserConfig config) {
        advertisersList.add(config);
    }

    @Override
    public void onAdvertiserToggled(Storage.AdvertiserConfig config, boolean enabled) {
        if (enabled) startAdvertising(config);
        else stopAdvertising(config);
    }

    private void withBluetooth(Continuation continuation) {
        withPermission(CONNECT_PERMISSION, new Continuation() {
            @SuppressLint("MissingPermission")
            @Override
            public void onSuccess() {
                BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
                if (adapter == null) {
                    Log.w(LOG_TAG, "No bluetooth adapters found");
                    continuation.onFailure();
                    return;
                }

                if (adapter.isEnabled()) {
                    continuation.onSuccess();
                } else {
                    getPending(ENABLE_BLUETOOTH_REQUEST).add(continuation);
                    Intent intent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                    startActivityForResult(intent, ENABLE_BLUETOOTH_REQUEST);
                }
            }

            @Override
            public void onFailure() {
                continuation.onFailure();
            }
        });
    }

    private void withBluetoothAdvertising(Continuation continuation) {
        withBluetooth(new Continuation() {
            @Override
            public void onSuccess() { withPermission(ADVERTISE_PERMISSION, continuation); }
            @Override
            public void onFailure() { continuation.onFailure(); }
        });
    }

    private void startAdvertising(Storage.AdvertiserConfig config) {
        withBluetoothAdvertising(new Continuation() {
            @SuppressLint("MissingPermission")
            @Override
            public void onSuccess() {
                BluetoothLeAdvertiser advertiser =
                    BluetoothAdapter.getDefaultAdapter().getBluetoothLeAdvertiser();

                AdvertiseSettings settings = (new AdvertiseSettings.Builder())
                    .setAdvertiseMode(config.mode)
                    .setConnectable(false)
                    .setTxPowerLevel(config.power)
                    .build();

                AdvertiseData data = (new AdvertiseData.Builder())
                    .setIncludeDeviceName(config.includeDeviceName)
                    .setIncludeTxPowerLevel(config.includeTxPowerLevel)
                    .build();

                AdvertiseCallback advertiseCallback = new AdvertiseCallback() {
                    @Override
                    public void onStartSuccess(AdvertiseSettings settingsInEffect) {
                        super.onStartSuccess(settingsInEffect);
                        Log.i(LOG_TAG, "onStartSuccess");
                    }

                    @Override
                    public void onStartFailure(int errorCode) {
                        super.onStartFailure(errorCode);
                        Log.i(LOG_TAG, "onStartFailure");
                    }
                };

                advertiseCallbacks.put(config.id, advertiseCallback);
                advertiser.startAdvertising(settings, data, advertiseCallback);
            }

            @Override
            public void onFailure() {
                advertisersList.setTumbler(config.id, false);
            }
        });
    }

    @SuppressLint("MissingPermission")
    void stopAdvertising(Storage.AdvertiserConfig config) {
        if (!advertiseCallbacks.containsKey(config.id))
            return;

        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        if (adapter == null || !adapter.isEnabled())
            return;

        withPermission(ADVERTISE_PERMISSION, optimistic(() -> {
            adapter.getBluetoothLeAdvertiser()
                .stopAdvertising(advertiseCallbacks.remove(config.id));
        }));
    }

    private ArrayDeque<Continuation> getPending(int key) {
        if (pending.containsKey(key)) {
            return pending.get(key);
        } else {
            ArrayDeque<Continuation> rs = new ArrayDeque<>();
            pending.put(key, rs);
            return rs;
        }
    }

    private void runPending(int key, boolean branch) {
        ArrayDeque<Continuation> cs = getPending(key);
        for (Continuation c: cs) {
            if (branch) c.onSuccess();
            else c.onFailure();
        }
        cs.clear();
    }

    private void withPermission(int code, Continuation continuation) {
        String[] permissions;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            permissions = new String[]{
                Manifest.permission.BLUETOOTH_ADVERTISE,
                Manifest.permission.BLUETOOTH_CONNECT
            };
        } else {
            continuation.onSuccess();
            return;
        }

        if (ActivityCompat.checkSelfPermission(
            MainActivity.this, permissions[code]
        ) == PackageManager.PERMISSION_GRANTED) {
            continuation.onSuccess();
        } else {
            Log.w(LOG_TAG, "Requesting user permission for " + permissions[code]);
            getPending(code).add(continuation);
            ActivityCompat.requestPermissions(
                MainActivity.this,
                new String[]{permissions[code]}, code);
        }
    }

    // Turns a Runnable into a Continuation (the Runnable is invoked on success).
    private Continuation optimistic(Runnable r) {
        return new Continuation() {
            @Override
            public void onSuccess() { r.run(); }
            @Override
            public void onFailure() { }
        };
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        runPending(
            requestCode, grantResults.length > 0 &&
            grantResults[0] == PackageManager.PERMISSION_GRANTED);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        runPending(
            requestCode,
            resultCode == RESULT_OK);
    }
}
