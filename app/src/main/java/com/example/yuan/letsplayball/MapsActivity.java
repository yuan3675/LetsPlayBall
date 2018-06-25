package com.example.yuan.letsplayball;

import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Build;
import android.os.Bundle;
import android.os.AsyncTask;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.TimePicker;
import android.graphics.Color;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.places.GeoDataClient;
import com.google.android.gms.location.places.PlaceDetectionClient;
import com.google.android.gms.location.places.Places;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.CameraPosition;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.maps.android.PolyUtil;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.net.URL;
import java.net.HttpURLConnection;
import java.util.HashMap;
import java.util.List;


public class MapsActivity extends AppCompatActivity implements GoogleMap.OnInfoWindowClickListener, OnMapReadyCallback{
    private static final String TAG = MapsActivity.class.getSimpleName();
    private GoogleMap mMap;
    private CameraPosition mCameraPosition;

    // The entry points to the Places API.
    private GeoDataClient mGeoDataClient;
    private PlaceDetectionClient mPlaceDetectionClient;

    // Add place dialog view
    private EditText mName;
    private EditText mAddress;
    private Spinner mCourtType;
    private View addPlaceView;

    //Add play dialog view
    private Spinner mPlayType;
    private Spinner mPlayLocation;
    private TimePicker mPlayTime;
    private View addPlayView;

    // Map Marker storage and polyline
    private ArrayList<Marker> courtMarkers = new ArrayList<>();
    private ArrayList<Marker> userMarkers = new ArrayList<>();
    private ArrayList<Polyline> polylines = new ArrayList<>();

    // Internet Request
    private String request;
    private String response;

    // The entry point to the Fused Location Provider.
    private FusedLocationProviderClient mFusedLocationProviderClient;

    // A default location (Sydney, Australia) and default zoom to use when location permission is
    // not granted.
    private final LatLng mDefaultLocation = new LatLng(-33.8523341, 151.2106085);
    private static final int DEFAULT_ZOOM = 15;
    private static final int PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION = 1;
    private boolean mLocationPermissionGranted;

    // The geographical location where the device is currently located. That is, the last-known
    // location retrieved by the Fused Location Provider.
    private Location mLastKnownLocation;

    // Keys for storing activity state.
    private static final String KEY_CAMERA_POSITION = "camera_position";
    private static final String KEY_LOCATION = "location";

    // Used for selecting the current place.
    private static final int M_MAX_ENTRIES = 5;
    private String[] mMenuItems = {"標記球場", "發起揪團", "取消揪團"};
    private boolean enableMapClick = false;

    // Used for server connection
    private String myDeviceModel = Build.DEVICE;
    private NetworkClient networkClient = new NetworkClient();

    private Handler timerHandler = new Handler();
    private Runnable timerRunnable = new Runnable() {
        @Override
        public void run() {
            updateUserInfo();
            updateCourtInfo();

            timerHandler.postDelayed(this, 60000);
        }
    };

    // Internet connection can not run in main thread
    private Runnable multiThread = new Runnable() {
        @Override
        public void run() {
            try {
                response = networkClient.connect(request);
            }
            catch (IOException e) { e.printStackTrace();}
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Retrieve location and camera position from saved instance state.
        if (savedInstanceState != null) {
            mLastKnownLocation = savedInstanceState.getParcelable(KEY_LOCATION);
            mCameraPosition = savedInstanceState.getParcelable(KEY_CAMERA_POSITION);
            if (mLastKnownLocation != null) {
                request = "{\"type\": 0 , \"deviceModel\": " + myDeviceModel + ", \"latitude\": " + mLastKnownLocation.getLatitude() + ", \"longitude\": " + mLastKnownLocation.getLongitude() + "}";
                // update device location to server
                Thread thread = new Thread(multiThread);
                thread.start();
                try {
                    thread.join();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                updateUserInfo();
            }
        }
        // Retrieve the content view that renders the map.
        setContentView(R.layout.activity_maps);

        // Set add place view
        addPlaceView = getLayoutInflater().inflate(R.layout.court_info_collection_dialog, null);
        mName = (EditText) addPlaceView.findViewById(R.id.location_name);
        mAddress = (EditText) addPlaceView.findViewById(R.id.address);
        mCourtType = (Spinner) addPlaceView.findViewById(R.id.court_type);
        ArrayAdapter<CharSequence> typeList = ArrayAdapter.createFromResource(MapsActivity.this, R.array.play_type, R.layout.support_simple_spinner_dropdown_item);
        typeList.setDropDownViewResource(R.layout.support_simple_spinner_dropdown_item);
        mCourtType.setAdapter(typeList);

        // Set add play view
        addPlayView = getLayoutInflater().inflate(R.layout.play_info_collection_dialog, null);

        mPlayType = (Spinner) addPlayView.findViewById(R.id.type_spinner);
        mPlayType.setAdapter(typeList);

        mPlayTime = (TimePicker) addPlayView.findViewById(R.id.play_time);
        mPlayLocation = (Spinner) addPlayView.findViewById(R.id.location_spinner);


        // Construct a GeoDataClient.
        mGeoDataClient = Places.getGeoDataClient(this);

        // Construct a PlaceDetectionClient.
        mPlaceDetectionClient = Places.getPlaceDetectionClient(this);

        // Construct a FusedLocationProviderClient.
        mFusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(this);

        // Build the map.
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.LetsPlayBall);
        mapFragment.getMapAsync(this);
    }

    /**
     * Saves the state of the map when the activity is paused.
     */

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        if (mMap != null) {
            outState.putParcelable(KEY_CAMERA_POSITION, mMap.getCameraPosition());
            outState.putParcelable(KEY_LOCATION, mLastKnownLocation);
            super.onSaveInstanceState(outState);
        }
    }

    /**
     * Sets up the options menu.
     * @param menu The options menu.
     * @return Boolean.
     */

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu, menu);
        return true;
    }

    /**
     * Handles a click on the menu option to get a place.
     * @param item The menu item to handle.
     * @return Boolean.
     */

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.menu) {
            openMenuDialog();
        }
        return true;
    }
    /**
     * Manipulates the map when it's available.
     * This callback is triggered when the map is ready to be used.
     */
    @Override
    public void onMapReady(GoogleMap map) {
        mMap = map;

        // Use a custom info window adapter to handle multiple lines of text in the
        // info window contents.
        mMap.setInfoWindowAdapter(new GoogleMap.InfoWindowAdapter() {

            @Override
            // Return null here, so that getInfoContents() is called next.
            public View getInfoWindow(Marker arg0) {
                return null;
            }

            @Override
            public View getInfoContents(Marker marker) {
                // Inflate the layouts for the info window, title and snippet.
                View infoWindow = getLayoutInflater().inflate(R.layout.custom_info_contents,
                        (FrameLayout) findViewById(R.id.LetsPlayBall), false);

                TextView title = ((TextView) infoWindow.findViewById(R.id.title));
                title.setText(marker.getTitle());

                TextView snippet = ((TextView) infoWindow.findViewById(R.id.snippet));
                snippet.setText(marker.getSnippet());

                return infoWindow;
            }
        });

        // Prompt the user for permission.
        getLocationPermission();

        // Turn on the My Location layer and the related control on the map.
        updateLocationUI();

        // Get the current location of the device and set the position of the map.
        getDeviceLocation();

        //Update  Info
        updateUserInfo();
        updateCourtInfo();

        //Listen map click event
        mMap.setOnMapClickListener(new GoogleMap.OnMapClickListener() {
            @Override
            public void onMapClick(LatLng latLng) {
                if (enableMapClick) {
                    if (addPlaceView.getParent() != null) {
                        ((ViewGroup)addPlaceView.getParent()).removeView(addPlaceView);
                    }
                    addCourtLabel(latLng);
                }
                else {
                    Log.v("MapClick", "not  enable");
                }
            }
        });

        // Listen info window click event
        mMap.setOnInfoWindowClickListener(this);

        // Start timet
        timerHandler.postDelayed(timerRunnable, 60000);
    }

    @Override
    public void onInfoWindowClick(Marker marker){
        getDeviceLocation();
        if (mLastKnownLocation != null) {
            LatLng origin = new LatLng(mLastKnownLocation.getLatitude(), mLastKnownLocation.getLongitude());
            LatLng dest = null;
            String temp = marker.getSnippet();
            String[] info = temp.split(",");
            if (info.length == 2) {
                String place = info[1];
                for (Marker courtMarker : courtMarkers) {
                    if (courtMarker.getTitle().equals(place)) {
                        dest = courtMarker.getPosition();
                        break;
                    }
                }
                if (dest == null) dest = marker.getPosition();
            }
            else dest = marker.getPosition();

            if (polylines.size() > 0) {
                for (Polyline polyline : polylines) {
                    polyline.remove();
                }
            }
            // Getting URL to the Google Directions API
            String url = getDirectionsUrl(origin, dest);
            DownloadTask downloadTask = new DownloadTask();
            // Start downloading json data from Google
            downloadTask.execute(url);
        }
    }

    private String getDirectionsUrl(LatLng origin, LatLng dest) {
        // Origin of route
        String str_origin = "origin=" + origin.latitude + "," + origin.longitude;
        // Destination of route
        String str_dest = "destination=" + dest.latitude + "," + dest.longitude;
        // Sensor enabled
        String sensor = "sensor=false";
        // Building the parameters to the web service
        String parameters = str_origin + "&" + str_dest + "&" + sensor;
        // Output format
        String output = "json";
        // Building the url to the web service
        String url = "https://maps.googleapis.com/maps/api/directions/" + output + "?" + parameters;
        return url;
    }

    /** A method to download json from url */
    private String downloadUrl(String strUrl) throws IOException {
        String data = "";
        InputStream iStream = null;
        HttpURLConnection urlConnection = null;
        try {
            URL url = new URL(strUrl);

            // Creating an http connection to communicate with url
            urlConnection = (HttpURLConnection)url.openConnection();

            // Connecting to url
            urlConnection.connect();

            // Reading data from url
            iStream = urlConnection.getInputStream();
            BufferedReader br = new BufferedReader(new InputStreamReader(iStream));
            StringBuffer sb = new StringBuffer();
            String line = "";
            while ((line = br.readLine()) != null) {
                sb.append(line);
            }
            data = sb.toString();
            br.close();
        } catch (Exception e) {
            Log.d("Url download exception", e.toString());
        } finally {
            iStream.close();
            urlConnection.disconnect();
        }
        return data;
    }

    /**
     * Adds the court information
     */
    public void addCourtLabel(final LatLng latLng) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        // Get the layout inflater
        LayoutInflater inflater = this.getLayoutInflater();

        // Inflate and set the layout for the dialog
        // Pass null as the parent view because its going in the dialog layout
        builder.setView(addPlaceView)
                // Add action buttons
                .setPositiveButton(R.string.Confirm, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int id) {
                        enableMapClick = false;
                        String type = mCourtType.getSelectedItem().toString();
                        String name = mName.getText().toString();
                        String address = mAddress.getText().toString();
                        addPlace(latLng, type, name, address);
                    }
                })
                .setNegativeButton(R.string.Cancel, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                    }
                })
                .show();
    }


    private void addPlace(LatLng latLng, String type, String name, String address) {
        request = "{\"type\": 1 ,  \"latitude\": " + latLng.latitude + ", \"longitude\": " + latLng.longitude + ",\"courtType\": " + type + ",\"name\": " + name + ",\"address\": " + address + "}";
        // update device location to server
        Thread thread = new Thread(multiThread);
        thread.start();
        try {
            thread.join();
        }
        catch (InterruptedException e) {
            e.printStackTrace();
        }
        updateCourtInfo();
    }

    private void addPlay(String playType, int hour, int minutes, String location, Double latitude, Double longitude) {
       request = "{\"deviceModel\": " + myDeviceModel + ",\"type\": 2 ,  \"latitude\": " + latitude + ", \"longitude\": " + longitude + ",\"playType\": " + playType + ",\"hour\":" + hour + ",\"minutes\": " + minutes + ",\"address\": " + location + "}";
        Thread thread = new Thread(multiThread);
        thread.start();
        try {
            thread.join();
        }
        catch (InterruptedException e) {
            e.printStackTrace();
        }
        updateUserInfo();
    }

    private void cancelPlay() {
        request = "{\"type\": 6, \"deviceModel\": " + myDeviceModel + "}";
        Thread thread = new Thread(multiThread);
        thread.start();
        try {
            thread.join();
        }
        catch (InterruptedException e) {
            e.printStackTrace();
        }
        updateUserInfo();
    }

    /**
     * Gets the current location of the device, and positions the map's camera.
     */
    private void getDeviceLocation() {
        /*
         * Get the best and most recent location of the device, which may be null in rare
         * cases when a location is not available.
         */
        try {
            if (mLocationPermissionGranted) {
                Task<Location> locationResult = mFusedLocationProviderClient.getLastLocation();
                locationResult.addOnCompleteListener(this, new OnCompleteListener<Location>() {
                    @Override
                    public void onComplete(@NonNull Task<Location> task) {
                        if (task.isSuccessful()) {
                            // Set the map's camera position to the current location of the device.
                            mLastKnownLocation = task.getResult();
                            if (mLastKnownLocation != null) {
                                mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(
                                        new LatLng(mLastKnownLocation.getLatitude(),
                                                mLastKnownLocation.getLongitude()), DEFAULT_ZOOM));
                                request = "{\"type\": 0 , \"deviceModel\": " + myDeviceModel + ", \"latitude\": " + mLastKnownLocation.getLatitude() + ", \"longitude\": " + mLastKnownLocation.getLongitude() + "}";
                                // update device location to server
                                Thread thread = new Thread(multiThread);
                                thread.start();
                                try {
                                    thread.join();
                                }
                                catch (InterruptedException e) {
                                    e.printStackTrace();
                                }
                                updateUserInfo();
                            }
                        } else {
                            Log.d(TAG, "Current location is null. Using defaults.");
                            Log.e(TAG, "Exception: %s", task.getException());
                            mMap.moveCamera(CameraUpdateFactory
                                    .newLatLngZoom(mDefaultLocation, DEFAULT_ZOOM));
                            mMap.getUiSettings().setMyLocationButtonEnabled(false);
                        }
                    }
                });
            }
        } catch (SecurityException e)  {
            Log.e("Exception: %s", e.getMessage());
        }
    }


    /**
     * Prompts the user for permission to use the device location.
     */
    private void getLocationPermission() {
        /*
         * Request location permission, so that we can get the location of the
         * device. The result of the permission request is handled by a callback,
         * onRequestPermissionsResult.
         */
        if (ContextCompat.checkSelfPermission(this.getApplicationContext(),
                android.Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
            mLocationPermissionGranted = true;
        } else {
            ActivityCompat.requestPermissions(this,
                    new String[]{android.Manifest.permission.ACCESS_FINE_LOCATION},
                    PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION);
        }
    }

    /**
     * Handles the result of the request for location permissions.
     */
    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String permissions[],
                                           @NonNull int[] grantResults) {
        mLocationPermissionGranted = false;
        switch (requestCode) {
            case PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION: {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    mLocationPermissionGranted = true;
                }
            }
        }
        updateLocationUI();
    }

    private void openMenuDialog() {
        // Ask the user to choose the place where they are now.
        DialogInterface.OnClickListener listener = new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                // The "which" argument contains the position of the selected item.
                String option = mMenuItems[which];
                if (mMenuItems[which] != null) {
                    if (mMenuItems[which].equals("標記球場")) {
                        openMapClickDialog();
                    }
                    else if (mMenuItems[which].equals("發起揪團")) {
                        if (addPlayView.getParent() != null) {
                            openAlreadyPlayDialog();
                        }
                        else {
                            openPlayInfoDialog();
                        }
                    }
                    else if (mMenuItems[which].equals("取消揪團")) {
                        if (addPlayView.getParent() != null) {
                            ((ViewGroup)addPlayView.getParent()).removeView(addPlayView);
                            openCancelPlayDialog();
                        }
                        else {
                            openNoPlayDialog();
                        }
                    }
                }
            }
        };

        // Display the dialog.
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(R.string.menu)
                .setItems(mMenuItems, listener)
                .show();
    }

    private void openMapClickDialog() {
        // Display the dialog.
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(R.string.map_click_message)
                .setNegativeButton("OK", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        enableMapClick = true;
                    }
                })
                .show();
    }

    private void openCancelPlayDialog() {
        // Display the dialog.
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(R.string.cancel_check_message)
                .setPositiveButton(R.string.Confirm, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        cancelPlay();
                    }
                })
                .setNegativeButton(R.string.Cancel, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                    }
                })
                .show();
    }

    private void openAlreadyPlayDialog() {
        // Display the dialog.
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(R.string.already_play_message)
                .setNegativeButton(R.string.Confirm, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                    }
                })
                .show();
    }

    private void openNoPlayDialog() {
        // Display the dialog.
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(R.string.no_play_message)
                .setNegativeButton(R.string.Confirm, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                    }
                })
                .show();
    }

    private void openPlayInfoDialog() {
        getDeviceLocation();
        ArrayAdapter<String> locationList = new ArrayAdapter<String>(this, R.layout.support_simple_spinner_dropdown_item);
        locationList.setDropDownViewResource(R.layout.support_simple_spinner_dropdown_item);
        mPlayLocation.setAdapter(locationList);
        request = "{\"type\": 4}";
        // Get court info from server
        Thread thread = new Thread(multiThread);
        thread.start();
        try {
            thread.join();
        }
        catch (InterruptedException e) {
            e.printStackTrace();
        }

        if (response != null) {
            try {
                JSONArray array = new JSONArray(response);
                for (int i = 0; i < array.length(); i++) {
                    JSONObject object = array.getJSONObject(i);
                    locationList.add(object.getString("name"));
                }
                locationList.notifyDataSetChanged();
            }
            catch (JSONException e){ e.printStackTrace(); }
        }
        else {
            Log.v("CourtInfoResponse", "Got null response");
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        // Get the layout inflater
        LayoutInflater inflater = this.getLayoutInflater();

        // Inflate and set the layout for the dialog
        // Pass null as the parent view because its going in the dialog layout
        builder.setView(addPlayView)
                // Add action buttons
                .setPositiveButton(R.string.Confirm, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int id) {
                        String type = mPlayType.getSelectedItem().toString();
                        int hour = mPlayTime.getHour();
                        int minute = mPlayTime.getMinute();
                        String loaction = mPlayLocation.getSelectedItem().toString();
                        if (mLastKnownLocation != null)
                            addPlay(type, hour, minute, loaction, mLastKnownLocation.getLatitude(), mLastKnownLocation.getLongitude());
                        else {
                            Log.v("mLastLocation", "null");
                        }
                    }
                })
                .setNegativeButton(R.string.Cancel, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                    }
                })
                .show();
    }

    // Clean the marker array
    private ArrayList<Marker> removeAllMarkers(ArrayList<Marker> markers) {
        for (int i = 0; i < markers.size(); i++) {
            markers.get(i).remove();
        }
        markers = new ArrayList<>();
        return  markers;
    }

    /**
     * Updates the map's UI settings based on whether the user has granted location permission.
     */
    private void updateLocationUI() {
        if (mMap == null) {
            return;
        }
        try {
            if (mLocationPermissionGranted) {
                mMap.setMyLocationEnabled(true);
                mMap.getUiSettings().setMyLocationButtonEnabled(true);
            } else {
                mMap.setMyLocationEnabled(false);
                mMap.getUiSettings().setMyLocationButtonEnabled(false);
                mLastKnownLocation = null;
                getLocationPermission();
            }
        } catch (SecurityException e)  {
            Log.e("Exception: %s", e.getMessage());
        }
    }

    // Update user info
    private void updateUserInfo() {
        JSONArray userArray = null;
        JSONArray playArray = null;
        userMarkers = removeAllMarkers(userMarkers);

        // Get all user info
        request = "{\"type\": 3}";
        Thread thread = new Thread(multiThread);
        thread.start();
        try {
            thread.join();
        }
        catch (InterruptedException e) {
            e.printStackTrace();
        }

        if (response != null) {
            try {
                userArray = new JSONArray(response);
            }
            catch (JSONException e){ e.printStackTrace(); }
        }
        else {
            Log.v("UserInfoResponse", "Got null response");
        }

        // Get all play info
        request = "{\"type\": 5}";
        // Get court info from server
        thread = new Thread(multiThread);
        thread.start();
        try {
            thread.join();
        }
        catch (InterruptedException e) {
            e.printStackTrace();
        }

        if (response != null) {
            try {
                playArray = new JSONArray(response);
            }
            catch (JSONException e){ e.printStackTrace(); }
        }

        // Do update
        if (userArray != null) {
            for (int i = 0; i < userArray.length(); i++) {
                try {
                    Marker marker = null;
                    JSONObject object = userArray.getJSONObject(i);
                    LatLng latLng = new LatLng(object.getDouble("latitude"), object.getDouble("longitude"));
                    boolean marker_flag = false;
                    if (playArray != null) {
                        for (int j = 0; j < playArray.length(); j++) {
                            JSONObject play = playArray.getJSONObject(j);
                            if (object.getString("deviceModel").equals(play.getString("deviceModel"))) {
                                marker = mMap.addMarker(new MarkerOptions()
                                        .position(latLng)
                                        .title(play.getString("playType"))
                                        .snippet(Integer.toString(play.getInt("hour")) + " : " + Integer.toString(play.getInt("minutes")) + " ," + play.getString("address"))
                                        .icon(BitmapDescriptorFactory.fromResource(R.drawable.play)));
                                marker_flag = true;
                                break;
                            }
                        }
                    }
                    if (!marker_flag) {
                        if (!myDeviceModel.equals(object.getString("deviceModel"))) {
                            marker = mMap.addMarker(new MarkerOptions()
                                    .position(latLng)
                                    .icon(BitmapDescriptorFactory.fromResource(R.drawable.other_user)));
                        }
                    }
                    if (marker != null) userMarkers.add(marker);
                }
                catch (JSONException e) {

                }
            }
        }
    }

    // Update court info
    private void updateCourtInfo() {
        courtMarkers = removeAllMarkers(courtMarkers);
        request = "{\"type\": 4}";
        // Get court info from server
        Thread thread = new Thread(multiThread);
        thread.start();
        try {
            thread.join();
        }
        catch (InterruptedException e) {
            e.printStackTrace();
        }

        if (response != null) {
            try {
                JSONArray array = new JSONArray(response);
                for (int i = 0; i < array.length(); i++) {
                    JSONObject object = array.getJSONObject(i);
                    LatLng latLng = new LatLng(object.getDouble("latitude"), object.getDouble("longitude"));
                    String type = object.getString("courtType");
                    Marker marker = null;
                    try {
                        if (type.equals("籃球"))
                            marker = mMap.addMarker(new MarkerOptions().position(latLng).title(object.getString("name")).snippet(object.getString("address")).icon(BitmapDescriptorFactory.fromResource(R.drawable.basketball)));
                        else if (type.equals("排球"))
                            marker = mMap.addMarker(new MarkerOptions().position(latLng).title(object.getString("name")).snippet(object.getString("address")).icon(BitmapDescriptorFactory.fromResource(R.drawable.volleyball_png_photo)));
                        else if (type.equals("羽球"))
                            marker = mMap.addMarker(new MarkerOptions().position(latLng).title(object.getString("name")).snippet(object.getString("address")).icon(BitmapDescriptorFactory.fromResource(R.drawable.badminton)));
                        else if (type.equals("網球"))
                            marker = mMap.addMarker(new MarkerOptions().position(latLng).title(object.getString("name")).snippet(object.getString("address")).icon(BitmapDescriptorFactory.fromResource(R.drawable.tennis)));
                        else if (type.equals("棒球"))
                            marker = mMap.addMarker(new MarkerOptions().position(latLng).title(object.getString("name")).snippet(object.getString("address")).icon(BitmapDescriptorFactory.fromResource(R.drawable.baseball)));
                        else
                            marker = mMap.addMarker(new MarkerOptions().position(latLng).title(object.getString("name")).snippet(object.getString("address")).icon(BitmapDescriptorFactory.fromResource(R.drawable.soccer)));
                        courtMarkers.add(marker);
                    }
                    catch (NullPointerException e) {}
                }
            }
            catch (JSONException e){ e.printStackTrace(); }
        }
        else {
            Log.v("CourtInfoResponse", "Got null response");
        }
    }

    public class DownloadTask extends AsyncTask<String, Void, String> {
        //  Downloading data in non-ui thread
        @Override
        protected String doInBackground(String... url) {
            // For storing data from web service
            String data = "";

            try {
                // Fetching the data form web service
                data = downloadUrl(url[0]);
                Log.v("Data", data);
            } catch (Exception e) {
                Log.d("Background Task", e.toString());
            }
            return data;
        }

        // Executes in UI thread, after the execution of doInBackground()
        @Override
        protected void onPostExecute(String result) {
            super.onPostExecute(result);

            ParserTask parserTask = new ParserTask();
            // Invokes the thread for parsing the JSON data
            parserTask.execute(result);
        }
    }

    public class ParserTask extends AsyncTask<String, Integer, String[]> {
        // Parsing the data in non-ui thread
        @Override
        protected String[] doInBackground(String... jsonData) {

            JSONObject jObject;
            String[] routes = null;

            try{
                jObject = new JSONObject(jsonData[0]);
                DirectionsJSONParser parser = new DirectionsJSONParser();

                // Starts parsing data
                routes = parser.parse(jObject);
            }catch(Exception e){
                e.printStackTrace();
            }
            return routes;
        }

        // Executes in UI thread, after the parsing process
        @Override
        protected void onPostExecute(String[] result) {
            int count = result.length;


            // Traversing through all the routes
            for(int i=0;i < count; i++){
                PolylineOptions options = new PolylineOptions();
                options.color(Color.RED);
                options.width(10);
                options.addAll(PolyUtil.decode(result[i]));
                polylines.add(mMap.addPolyline(options));
            }
        }
    }
}
