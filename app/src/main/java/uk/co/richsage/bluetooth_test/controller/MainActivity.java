package uk.co.richsage.bluetooth_test.controller;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.UUID;


public class MainActivity extends Activity {

    private final int REQUEST_ENABLE_BT = 1;

    private BluetoothAdapter bluetoothAdapter;
    private ArrayAdapter<String> arrayAdapter;
    private ListView listView;
    private ConnectThread connectThread;
    private ConnectedThread connectedThread;
    private BluetoothDevice currentDevice;

    // Create a BroadcastReceiver for ACTION_FOUND
    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            // When discovery finds a device
            if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                // Get the BluetoothDevice object from the Intent
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);

                String name = device.getName();

                // Add the name and address to an array adapter to show in a ListView
                log("Found a device: " + name);
                arrayAdapter.add(device.getAddress());
            }
        }
    };

    private void log(String text) {
        TextView textView = (TextView) findViewById(R.id.textView2);
        textView.append(text + "\n");
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (bluetoothAdapter == null) {
            Toast.makeText(this, "Bluetooth is not available", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        arrayAdapter = new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1);
        listView = (ListView) findViewById(R.id.listView);
        listView.setAdapter(arrayAdapter);
    }

    public void onStart() {
        super.onStart();

        if (!bluetoothAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
            log("Turn BT on");
        } else {
            log("BT is on already");
        }

        // Bind click handler to list rows
        // to connect to a device and send it a message
        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                String address = arrayAdapter.getItem(position);
                if (currentDevice != null && currentDevice.getAddress().equals(address)) {
                    log("Same device: sending data...");
                    connectedThread.write("Hello, world (subsequent)".getBytes());
                    // Just send data
                } else {
                    log("Selecting device to connect to");

                    log("- " + address);
                    BluetoothDevice device = bluetoothAdapter.getRemoteDevice(address);
                    currentDevice = device;

                    switch (device.getBondState()) {
                        case BluetoothDevice.BOND_NONE:
                            log("- Bonding: NONE");
                            break;
                        case BluetoothDevice.BOND_BONDED:
                            log("- Bonding: Bonded");
                            break;
                        case BluetoothDevice.BOND_BONDING:
                            log("- Bonding: Bonding currently");
                            break;
                    }

                    log("Connecting...");
                    if (connectThread != null) {
                        connectThread.interrupt();
                        connectThread = null;
                    }
                    connectThread = new ConnectThread(device);
                    connectThread.start();
                }
            }
        });
    }

    public void onDestroy() {
        super.onDestroy();
        unregisterReceiver(mReceiver);
        connectThread = null;
        connectedThread = null;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();
        if (id == R.id.action_find_devices) {

            // Find all devices
            // Register the BroadcastReceiver to handle when we get told about a new device
            log("Searching for devices...");
            arrayAdapter.clear();

            IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
            registerReceiver(mReceiver, filter); // Don't forget to unregister during onDestroy
            bluetoothAdapter.startDiscovery();

            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQUEST_ENABLE_BT) {
            if (resultCode != RESULT_OK) {
                android.util.Log.d("Controller", "EEK! Bluetooth not enabled");
            }
        }
    }

    /**
     * Thread that connects to another device
     */
    private class ConnectThread extends Thread {
        private final BluetoothSocket mmSocket;
        private final BluetoothDevice mmDevice;

        public ConnectThread(BluetoothDevice device) {
            // Use a temporary object that is later assigned to mmSocket,
            // because mmSocket is final
            BluetoothSocket tmp = null;
            mmDevice = device;

            // Get a BluetoothSocket to connect with the given BluetoothDevice
            try {
                // MY_UUID is the app's UUID string, also used by the server code
                UUID ourUUID = UUID.fromString(getString(R.string.uuid));
                tmp = device.createInsecureRfcommSocketToServiceRecord(ourUUID);
            } catch (IOException e) { }
            mmSocket = tmp;
        }

        public void run() {
            // Cancel discovery because it will slow down the connection
            bluetoothAdapter.cancelDiscovery();

            try {
                // Connect the device through the socket. This will block
                // until it succeeds or throws an exception
                mmSocket.connect();
            } catch (IOException connectException) {
                // Unable to connect; close the socket and get out
                connectException.printStackTrace();

                try {
                    mmSocket.close();
                } catch (IOException closeException) {
                    closeException.printStackTrace();
                }
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        log("Couldn't connect");
                    }
                });

                return;
            }

            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    log("Connected OK!");
                }
            });

            // Do work to manage the connection (in a separate thread)
            manageConnectedSocket(mmSocket);
        }

        /** Will cancel an in-progress connection, and close the socket */
        public void cancel() {
            try {
                mmSocket.close();
            } catch (IOException e) { }
        }

        protected void manageConnectedSocket(BluetoothSocket socket) {
            connectedThread = null;
            connectedThread = new ConnectedThread(socket);
            connectedThread.start();

            connectedThread.write("Hello, world!".getBytes());
        }
    }

    /**
     * Thread to handle read/write to BT socket
     */
    private class ConnectedThread extends Thread {
        private final BluetoothSocket mmSocket;
        private final InputStream mmInStream;
        private final OutputStream mmOutStream;

        public ConnectedThread(BluetoothSocket socket) {
            mmSocket = socket;
            InputStream tmpIn = null;
            OutputStream tmpOut = null;

            // Get the input and output streams, using temp objects because
            // member streams are final
            try {
                tmpIn = socket.getInputStream();
                tmpOut = socket.getOutputStream();
            } catch (IOException e) { }

            mmInStream = tmpIn;
            mmOutStream = tmpOut;
        }

        public void run() {
            byte[] buffer = new byte[1024];  // buffer store for the stream
            int bytes; // bytes returned from read()

            // Keep listening to the InputStream until an exception occurs
            while (true) {
                try {
                    // Read from the InputStream
                    bytes = mmInStream.read(buffer);
                } catch (IOException e) {
                    android.util.Log.d("Controller", "Exception when reading from the input stream");
                    e.printStackTrace();
                    break;
                }
            }
        }

        /* Call this from the main activity to send data to the remote device */
        public void write(byte[] bytes) {
            try {
                mmOutStream.write(bytes);
            } catch (IOException e) { }
        }

        /* Call this from the main activity to shutdown the connection */
        public void cancel() {
            try {
                mmSocket.close();
            } catch (IOException e) { }
        }
    }
}
