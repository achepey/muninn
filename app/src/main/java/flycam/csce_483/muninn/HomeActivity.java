package flycam.csce_483.muninn;

import android.app.Activity;

import android.app.AlertDialog;
import android.app.Dialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.util.JsonReader;
import android.util.JsonWriter;
import android.util.Log;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Set;

public class HomeActivity extends Activity {

    final CharSequence[] modes = {"Hover", "Loop", "Follow-Me"};

    private boolean flightStatus = false; // 1 for in flight, 0 otherwise
    private boolean beaconStatus = false; // 1 for connected, 0 otherwise
    protected String currentView;

    private int hover_dist;
    private int loop_radius;
    private int follow_dist;

    private int selectedMode;

    protected Button launchLandButton;

    // Varying TextViews
    private TextView launchLandText;
    private TextView beacon_connection_text;

    // Input Number Fields
    private EditText hover_dist_input;
    private EditText loop_rad_input;
    private EditText follow_dist_input;

    // Fields for drone settings

    // Fields for Bluetooth
    private final int REQUEST_ENABLE_BT = 3;
    private ArrayList<BluetoothDevice> foundDevices;
    private BluetoothAdapter mBluetoothAdapter;
    private ArrayAdapter<String> btArrayAdapter;


    @Override
    public void setContentView(int layoutResID) {
        View view = getLayoutInflater().inflate(layoutResID, null);
        //tag is needed for pressing back button to go back to splash screen
        currentView = (String)view.getTag();
        super.setContentView(view);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getActionBar().hide();

        setContentView(R.layout.activity_home);

        // Setting TextViews
        launchLandText = (TextView) findViewById(R.id.launch_land_status);
        beacon_connection_text = (TextView) findViewById(R.id.beacon_status);

        // Setting Buttons
        launchLandButton = (Button) findViewById(R.id.button_launch_land);

        // Default values for auto flight distances
        hover_dist = 10;
        loop_radius = 25;
        follow_dist = 20;

        // Prompts user to connect to Muninn wifi and bluetooth manually
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setMessage("Make sure that you have connected to Muninn's wi-fi and bluetooth!");
        builder.setPositiveButton("OK", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                ;// purposely left open
            }
        });
        builder.create().show();

        // Creates handler to call the refreshSettings method every 10 seconds
        Handler handler = new Handler();
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                refreshSettings();
            }
        }, 10000);

        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

    }

    // Goes through the action of attempting to launch or land the device, sent via bluetooth
    public void launchLand(View view) {

        if(beaconStatus) {
            if(flightStatus) { // If in flight
                //send signals to land the drone
                launchLandButton.setText(R.string.landing);
                launchLandButton.setClickable(false);
                launchLandText.setText(R.string.landing);
            }
            else { // on the ground
                //send signals to launch the drone
                launchLandButton.setText(R.string.land);
                launchLandText.setText(R.string.in_flight);
            }
        }
        else
            connectBeacon(view);

    }

    // Switches view to the application settings menu
    public void appSettings(View view){
        setContentView(R.layout.app_settings_menu);

        // Setting EditText Fields
        hover_dist_input = (EditText) findViewById(R.id.hover_dist_input);
        loop_rad_input = (EditText) findViewById(R.id.loop_rad_input);
        follow_dist_input = (EditText) findViewById(R.id.follow_dist_input);

        // Sets the input as what the user has saved already
        hover_dist_input.setText(Integer.toString(hover_dist));
        loop_rad_input.setText(Integer.toString(loop_radius));
        follow_dist_input.setText(Integer.toString(follow_dist));

    }

    public void saveSettings(View view) {
        hover_dist = Integer.parseInt(hover_dist_input.getText().toString());
        loop_radius = Integer.parseInt(loop_rad_input.getText().toString());
        follow_dist = Integer.parseInt(follow_dist_input.getText().toString());

        // Get switch information

        Toast.makeText(getApplicationContext(), "Settings have been saved!", Toast.LENGTH_SHORT).show();
        setContentView(R.layout.activity_home);
    }

    // Updates the current mode selection
    public void selectMode(View view) {

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.select_mode)
                .setSingleChoiceItems(R.array.mode_select_array, selectedMode, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                        Toast.makeText(getApplicationContext(), "Muninn is now set to " + modes[which] + " mode!", Toast.LENGTH_SHORT).show();
                        selectedMode = which;
                    }
                });
        builder.create().show();
    }

    public void goToCamera(View view) {
        startActivity(new Intent(this, CameraActivity.class));
    }

    private void setupBT() {

        requestBTOn();
        showBTDevices();

    }

    private void requestBTOn() {
        if(mBluetoothAdapter == null) {
            // Device does not support Bluetooth
            Toast.makeText(getApplicationContext(), "This device does not support bluetooth. Please use this companion app with an appropriate device.", Toast.LENGTH_LONG).show();
        }
        else {
            if (!mBluetoothAdapter.isEnabled()) {
                Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
            }

        }
    }

    private void showBTDevices() {
        AlertDialog.Builder builder = new AlertDialog.Builder(getApplicationContext());
        builder.setView(R.layout.bluetooth_devices_menu);
        builder.setTitle("Please choose a paired device");
        builder.create().show();

        ListView listView = (ListView) findViewById(R.id.btDevicesList);

        foundDevices = new ArrayList<>(mBluetoothAdapter.getBondedDevices());
        // If there are paired devices
        if (foundDevices.size() > 0) {
            // Loop through paired devices
            for (BluetoothDevice device : foundDevices) {
                // Add the name and address to an array adapter to show in a ListView
                btArrayAdapter.add(device.getName() + "\n" + device.getAddress());
            }
            listView.setAdapter(btArrayAdapter);
        }
    }


    // Goes through the process of connecting the phone to the beacon
    public void connectBeacon(View view) {

        if(!beaconStatus) {

            // Code to connect to beacon


            //Creates pop-up letting user know the whether or not the beacon was successfully connected
            Toast.makeText(getApplicationContext(), "Beacon NOT connected!", Toast.LENGTH_SHORT).show();
            if (beaconStatus) {
               Toast.makeText(getApplicationContext(), "Beacon connected!", Toast.LENGTH_SHORT).show();
            }
        }
        else {
            Toast.makeText(getApplicationContext(), "Beacon is already connected!", Toast.LENGTH_SHORT).show();
        }

    }

    // Will be used to refresh the connections, update drone status, etc.
    // Will connect to the beacon to retrieve the latest information
    private void refreshSettings() {
        Log.d("main", "Settings Refreshed");
        //retrieve information

        if(!flightStatus /*&& still in flight via signals sent: still in the process of landing*/) {
            launchLandText.setText(R.string.landing);
            launchLandButton.setText(R.string.landing);
        }
        else if(!flightStatus /*&& landed via signals*/) {
            launchLandText.setText(R.string.landed);
            launchLandButton.setText(R.string.launch);
            launchLandButton.setClickable(true);
        }

    }

    private void parseJSON(InputStream in) {
        try {
            JsonReader reader = new JsonReader(new InputStreamReader(in, "UTF-8"));
            reader.beginObject();
            while(reader.hasNext()){
                String name = reader.nextName();
                if(name.equals("drone_battery")){

                }
                else if(name.equals("drone_connection")){

                }
                else if(name.equals("drone_status")) {

                }
                else
                    reader.skipValue();
            }
            reader.endObject();

        }
        catch(UnsupportedEncodingException e) {
            // do something
        }
        catch(IOException e){
            // do something
        }
    }

    private void writeJSONtoFile() {
        File file = new File(getExternalCacheDir(), "testJSON.json");
        try {
            FileOutputStream out = new FileOutputStream(file);
            generateJSON(out);
        }
        catch (IOException e) {
            // do something
        }
    }

    private void generateJSON(OutputStream out) {

        try {
            //Json Writer object
            JsonWriter writer = new JsonWriter(new OutputStreamWriter(out, "UTF-8"));
            writer.setIndent("    ");
            writer.beginObject();
            writer.name("testBool").value(true);
            writer.name("testInt").value(12);
            writer.name("testString").value("Hello, World!");
            writer.endObject();
            writer.close();

            /*
            flight_mode -> (loop, hover, follow)
            launch_land -> launch, land, keep
            hover_dist
            follow_dist
            loop_rad
             */

        }
        catch (UnsupportedEncodingException e){
            // do something
        }
        catch (IOException e) {
            // do something
        }
    }

    @Override
    public void onBackPressed() {
        //Checks if the current screen is not the activity_main.xml layout
        if (!"main".equals(currentView)) {
            setContentView(R.layout.activity_home);
        }
        else
            super.onBackPressed();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.home, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

}