package com.example.rccar;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class MainActivity extends Activity {
    private static final int REQ_BT = 100;
    private static final UUID SPP_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

    private WebView webView;
    private BluetoothAdapter adapter;
    private BluetoothSocket socket;
    private InputStream in;
    private OutputStream out;
    private Thread readerThread;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        webView = findViewById(R.id.webview);
        WebSettings ws = webView.getSettings();
        ws.setJavaScriptEnabled(true);
        ws.setDomStorageEnabled(true);
        ws.setBuiltInZoomControls(false);
        webView.setWebViewClient(new WebViewClient());
        webView.addJavascriptInterface(new BluetoothBridge(), "AndroidBluetooth");
        webView.loadUrl("file:///android_asset/index.html");

        adapter = BluetoothAdapter.getDefaultAdapter();
    }

    private boolean hasBtPermission() {
        if (android.os.Build.VERSION.SDK_INT >= 31) {
            return checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED;
        }
        return true;
    }

    private void ensurePermissionAndChoose() {
        if (adapter == null) {
            toast("This phone does not support Bluetooth.");
            setDisconnectedJs();
            return;
        }
        if (!hasBtPermission()) {
            if (android.os.Build.VERSION.SDK_INT >= 31) {
                requestPermissions(new String[]{Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN}, REQ_BT);
            }
            return;
        }
        if (!adapter.isEnabled()) {
            startActivity(new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE));
            return;
        }
        choosePairedDevice();
    }

    private void choosePairedDevice() {
        try {
            Set<BluetoothDevice> bonded = adapter.getBondedDevices();
            if (bonded == null || bonded.isEmpty()) {
                toast("Pair the HC-05 in Android Bluetooth settings first.");
                setDisconnectedJs();
                return;
            }

            final List<BluetoothDevice> devices = new ArrayList<>(bonded);
            String[] labels = new String[devices.size()];
            for (int i = 0; i < devices.size(); i++) {
                String name = devices.get(i).getName();
                labels[i] = (name == null ? "Unknown device" : name) + "\n" + devices.get(i).getAddress();
            }

            new AlertDialog.Builder(this)
                    .setTitle("Select Bluetooth device")
                    .setItems(labels, (dialog, which) -> connectTo(devices.get(which)))
                    .setNegativeButton("Cancel", (d, w) -> setDisconnectedJs())
                    .show();
        } catch (SecurityException e) {
            toast("Bluetooth permission is required.");
            setDisconnectedJs();
        }
    }

   private void connectTo(BluetoothDevice device) {
    closeSocket();
    mainHandler.post(() -> callJs("setStatus('connecting')"));

    new Thread(() -> {
        BluetoothSocket s = null;

        try {
            // Stop Bluetooth discovery before attempting the connection
            try {
                adapter.cancelDiscovery();
            } catch (SecurityException ignored) {
            }

            // First try the normal secure RFCOMM connection
            try {
                s = device.createRfcommSocketToServiceRecord(SPP_UUID);
                s.connect();

            } catch (Exception secureError) {

                // Secure connection failed.
                // Try an insecure RFCOMM connection, which is commonly
                // needed with HC-05 modules.
                try {
                    if (s != null) {
                        s.close();
                    }
                } catch (Exception ignored) {
                }

                s = device.createInsecureRfcommSocketToServiceRecord(SPP_UUID);
                s.connect();
            }

            socket = s;
            in = s.getInputStream();
            out = s.getOutputStream();

            String name;
            try {
                name = device.getName();
            } catch (SecurityException e) {
                name = "HC-05";
            }

            final String deviceName =
                    name == null ? "HC-05" : name;

            mainHandler.post(() ->
                    callJs("onAndroidConnected(" +
                            jsQuote(deviceName) + ")")
            );

            startReader();

        } catch (Exception e) {

            try {
                if (s != null) {
                    s.close();
                }
            } catch (Exception ignored) {
            }

            closeSocket();

            mainHandler.post(() -> {
                toast("Could not connect to HC-05.");
                setDisconnectedJs();
            });
        }
    }).start();
}
    private void startReader() {
        readerThread = new Thread(() -> {
            byte[] buffer = new byte[256];
            StringBuilder line = new StringBuilder();
            try {
                while (socket != null && socket.isConnected()) {
                    int n = in.read(buffer);
                    if (n < 0) break;
                    for (int i = 0; i < n; i++) {
                        char c = (char)(buffer[i] & 0xFF);
                        if (c == '\n' || c == '\r') {
                            if (line.length() > 0) {
                                final String msg = line.toString();
                                line.setLength(0);
                                mainHandler.post(() -> callJs("onBtData(" + jsQuote(msg) + ")"));
                            }
                        } else {
                            line.append(c);
                        }
                    }
                }
            } catch (IOException ignored) {
            } finally {
                closeSocket();
                mainHandler.post(this::setDisconnectedJs);
            }
        });
        readerThread.start();
    }

    private String jsQuote(String s) {
        if (s == null) return "''";
        return "'" + s.replace("\\", "\\\\").replace("'", "\\'").replace("\n", "\\n").replace("\r", "\\r") + "'";
    }

    private void callJs(String js) {
        if (webView != null) webView.evaluateJavascript("javascript:" + js, null);
    }

    private void setDisconnectedJs() {
        callJs("onAndroidDisconnected()");
    }

    private void toast(String text) {
        Toast.makeText(this, text, Toast.LENGTH_SHORT).show();
    }

    private synchronized void closeSocket() {
        try { if (in != null) in.close(); } catch (Exception ignored) {}
        try { if (out != null) out.close(); } catch (Exception ignored) {}
        try { if (socket != null) socket.close(); } catch (Exception ignored) {}
        in = null;
        out = null;
        socket = null;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_BT) {
            if (hasBtPermission()) choosePairedDevice();
            else { toast("Bluetooth permission denied."); setDisconnectedJs(); }
        }
    }

    @Override
    protected void onDestroy() {
        closeSocket();
        super.onDestroy();
    }

    public class BluetoothBridge {
        @JavascriptInterface
        public void connect() { mainHandler.post(() -> ensurePermissionAndChoose()); }

        @JavascriptInterface
        public void disconnect() { mainHandler.post(() -> { closeSocket(); setDisconnectedJs(); }); }

        @JavascriptInterface
        public void send(String command) {
            if (command == null || command.length() == 0) return;
            synchronized (MainActivity.this) {
                if (out != null) {
                    try {
                        out.write(command.getBytes(java.nio.charset.StandardCharsets.US_ASCII));
                        out.flush();
                    } catch (IOException e) {
                        mainHandler.post(() -> setDisconnectedJs());
                    }
                }
            }
        }
    }
}
