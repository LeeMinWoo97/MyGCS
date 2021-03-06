package com.LSH.mygcs;

import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.support.v4.app.FragmentManager;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.LSH.mygcs.activites.helpers.BluetoothDevicesActivity;
import com.LSH.mygcs.utils.TLogUtils;
import com.LSH.mygcs.utils.prefs.DroidPlannerPrefs;
import com.MAVLink.ardupilotmega.CRC;
import com.MAVLink.enums.MAV_AUTOPILOT;
import com.MAVLink.enums.MAV_CMD;
import com.naver.maps.geometry.LatLng;
import com.naver.maps.map.CameraUpdate;
import com.naver.maps.map.LocationTrackingMode;
import com.naver.maps.map.MapFragment;
import com.naver.maps.map.NaverMap;
import com.naver.maps.map.OnMapReadyCallback;
import com.naver.maps.map.UiSettings;
import com.naver.maps.map.overlay.Marker;
import com.naver.maps.map.overlay.PolylineOverlay;
import com.naver.maps.map.util.FusedLocationSource;
import com.o3dr.android.client.ControlTower;
import com.o3dr.android.client.Drone;
import com.o3dr.android.client.apis.ControlApi;
import com.o3dr.android.client.apis.VehicleApi;
import com.o3dr.android.client.interfaces.DroneListener;
import com.o3dr.android.client.interfaces.LinkListener;
import com.o3dr.android.client.interfaces.TowerListener;
import com.o3dr.services.android.lib.coordinate.LatLong;
import com.o3dr.services.android.lib.drone.attribute.AttributeEvent;
import com.o3dr.services.android.lib.drone.attribute.AttributeType;
import com.o3dr.services.android.lib.drone.companion.solo.SoloAttributes;
import com.o3dr.services.android.lib.drone.companion.solo.SoloState;
import com.o3dr.services.android.lib.drone.connection.ConnectionParameter;
import com.o3dr.services.android.lib.drone.connection.ConnectionType;
import com.o3dr.services.android.lib.drone.mission.item.spatial.Waypoint;
import com.o3dr.services.android.lib.drone.property.Altitude;
import com.o3dr.services.android.lib.drone.property.Attitude;
import com.o3dr.services.android.lib.drone.property.Battery;
import com.o3dr.services.android.lib.drone.property.Gps;
import com.o3dr.services.android.lib.drone.property.GuidedState;
import com.o3dr.services.android.lib.drone.property.Speed;
import com.o3dr.services.android.lib.drone.property.State;
import com.o3dr.services.android.lib.drone.property.Type;
import com.o3dr.services.android.lib.drone.property.VehicleMode;
import com.o3dr.services.android.lib.gcs.link.LinkConnectionStatus;
import com.o3dr.services.android.lib.model.AbstractCommandListener;
import com.o3dr.services.android.lib.model.SimpleCommandListener;

import org.droidplanner.services.android.impl.core.MAVLink.MavLinkMsgHandler;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity implements DroneListener, TowerListener, LinkListener, OnMapReadyCallback {

    private ArrayList<LatLng> mLocationCollection = new ArrayList<LatLng>();
    private ArrayList<String> mTextInRecycleerView = new ArrayList<>();
    private Boolean mCameraFix = false;
    private ControlTower controlTower;
    private Drone drone;
    private FusedLocationSource mLocationSource;
    private MapFragment mMapFragment;
    private Marker dronePosition = new Marker();
    private NaverMap mNaverMap;
    private PolylineOverlay mPolyline = new PolylineOverlay();
    private RecyclerView mRecyclerView;
    private Spinner mModeSelector;
    private int droneType = Type.TYPE_UNKNOWN;
    private int mABdistance = 30;
    private double mAltitude = 3.0;
    private double mFlightwidth = 3.0;
    private static final String TAG = MainActivity.class.getSimpleName();
    private static final int DEFAULT_UDP_PORT = 14550;
    private static final int DEFAULT_USB_BAUD_RATE = 57600;
    private static final int LOCATION_PERMISSION_REQUEST_CODE = 1000;
    private final Handler handler = new Handler();

    ConnectionParameter connParams;

    Handler mainHandler;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mLocationSource =
                new FusedLocationSource(this, LOCATION_PERMISSION_REQUEST_CODE);

        FragmentManager fm = getSupportFragmentManager();
        mMapFragment = (MapFragment) fm.findFragmentById(R.id.map);
        if (mMapFragment == null) {
            mMapFragment = MapFragment.newInstance();
            fm.beginTransaction().add(R.id.map, mMapFragment).commit();
        }
        mMapFragment.getMapAsync(this);

        final Context context = getApplicationContext();
        this.controlTower = new ControlTower(context);
        this.drone = new Drone(context);


        this.mModeSelector = (Spinner) findViewById(R.id.modeSelector);
        this.mModeSelector.setOnItemSelectedListener(new Spinner.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                onFlightModeSelected(view);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                // Do nothing
            }
        });

        mainHandler = new Handler(getApplicationContext().getMainLooper());

        // ????????????????????? LinearLayoutManager ?????? ??????.
        mRecyclerView = findViewById(R.id.stateText);
        mRecyclerView.setLayoutManager(new LinearLayoutManager(this));


    }

    @Override
    public void onMapReady(@NonNull NaverMap naverMap) {
        this.mNaverMap = naverMap;
        GuideMode guidemode = new GuideMode();
        Marker target = new Marker();
        UiSettings uiSettings = naverMap.getUiSettings();
        uiSettings.setZoomControlEnabled(false);
        uiSettings.setScaleBarEnabled(false);
        naverMap.setMapType(NaverMap.MapType.Satellite);
        //mLocationOverlay = naverMap.getLocationOverlay();
        //mLocationOverlay.setVisible(true);
        //mLocationOverlay.setIcon(OverlayImage.fromResource(R.drawable.location_overlay_icon));
        naverMap.setLocationSource(mLocationSource);
        naverMap.setLocationTrackingMode(LocationTrackingMode.NoFollow);
        //dronePosition.setIcon(OverlayImage.fromResource(R.drawable.drone));
        naverMap.setOnMapLongClickListener((point, coord) -> {
                    target.setPosition(new LatLng(coord.latitude, coord.longitude));
                    target.setMap(naverMap);
                    addRecyclerViewText("????????? ?????? ???????????? ???????????????.");
                    if (guidemode.DialogSimple(drone, new LatLong(coord.latitude, coord.longitude), this)) {
                        addRecyclerViewText("????????? ??????????????? ?????????????????????.");
                    } else {
                        addRecyclerViewText("?????? ?????? ??????");
                    }
                    target.setMap(null);

                }
        );
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (mLocationSource.onRequestPermissionsResult(
                requestCode, permissions, grantResults)) {
            if (!mLocationSource.isActivated()) { // ?????? ?????????
                mNaverMap.setLocationTrackingMode(LocationTrackingMode.None);
            }
            return;
        }
        super.onRequestPermissionsResult(
                requestCode, permissions, grantResults);
    }

    @Override
    public void onStart() {
        super.onStart();
        this.controlTower.connect(this);
        //updateVehicleModesForType(this.droneType);
    }

    @Override
    public void onStop() {
        super.onStop();
        if (this.drone.isConnected()) {
            this.drone.disconnect();
            //updateConnectedButton(false);
        }

        this.controlTower.unregisterDrone(this.drone);
        this.controlTower.disconnect();
    }

    //about Drone Event

    @Override
    public void onDroneEvent(String event, Bundle extras) {
        Button connetcButton = (Button) findViewById(R.id.connectBtn);
        switch (event) {
            case AttributeEvent.STATE_CONNECTED:
                alertUser("Drone Connected");
                connetcButton.setText("Disconnect");
                addRecyclerViewText("?????? ??????");
/*
                updateConnectedButton(this.drone.isConnected());
                updateArmButton();
 */
                checkSoloState();
                break;

            case AttributeEvent.STATE_DISCONNECTED:
                alertUser("Drone Disconnected");
                connetcButton.setText("Connect");
                addRecyclerViewText("?????? ?????? ??????");
/*
                updateConnectedButton(this.drone.isConnected());
                updateArmButton();
 */
                break;

            case AttributeEvent.STATE_UPDATED:
            case AttributeEvent.STATE_ARMING:
                updateArmButton();
                break;

            case AttributeEvent.TYPE_UPDATED:
                Type newDroneType = this.drone.getAttribute(AttributeType.TYPE);
                if (newDroneType.getDroneType() != this.droneType) {
                    this.droneType = newDroneType.getDroneType();

                    updateVehicleModesForType(this.droneType);

                }
                break;

            case AttributeEvent.STATE_VEHICLE_MODE:
                updateVehicleMode();
                break;

            case AttributeEvent.SPEED_UPDATED:
                updateSpeed();
                break;

            case AttributeEvent.ALTITUDE_UPDATED:
                updateAltitude();
                break;

            case AttributeEvent.HOME_UPDATED:
//                updateDistanceFromHome();
                break;

            case AttributeEvent.BATTERY_UPDATED:
                updateBattery();
                break;

            case AttributeEvent.GPS_COUNT:
                updateGPS();
                break;

            case AttributeEvent.ATTITUDE_UPDATED:
                updateAttitude();
                break;

            case AttributeEvent.GPS_POSITION:
                updateDronePosition();
                break;

            default:
                // Log.i("DRONE_EVENT", event); //Uncomment to see events from the drone
                break;
        }
    }

    protected void updateVehicleModesForType(int droneType) {
        List<VehicleMode> vehicleModes = VehicleMode.getVehicleModePerDroneType(droneType);
        ArrayAdapter<VehicleMode> vehicleModeArrayAdapter = new ArrayAdapter<VehicleMode>(
                this, android.R.layout.simple_spinner_item, vehicleModes);
        vehicleModeArrayAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        this.mModeSelector.setAdapter(vehicleModeArrayAdapter);
    }

    protected void updateVehicleMode() {
        State vehicleState = this.drone.getAttribute(AttributeType.STATE);
        VehicleMode vehicleMode = vehicleState.getVehicleMode();
        ArrayAdapter arrayAdapter = (ArrayAdapter) this.mModeSelector.getAdapter();
        this.mModeSelector.setSelection(arrayAdapter.getPosition(vehicleMode));
    }

    protected void updateSpeed() {
        TextView speedView = (TextView) findViewById(R.id.valueSpeed);
        Speed droneSpeed = this.drone.getAttribute(AttributeType.SPEED);
        speedView.setText(String.format("%3.1f", droneSpeed.getGroundSpeed()) + "m/s");
    }

    protected void updateAltitude() {
        TextView altitudeView = (TextView) findViewById(R.id.valueAltitude);
        Altitude droneAltitude = this.drone.getAttribute(AttributeType.ALTITUDE);
        altitudeView.setText(String.format("%3.1f", droneAltitude.getAltitude()) + "m");
    }

    protected void updateBattery() {
        TextView batteryView = (TextView) findViewById(R.id.valueVolt);
        Battery droneBattery = this.drone.getAttribute(AttributeType.BATTERY);
        batteryView.setText(String.format("%3.1f", droneBattery.getBatteryVoltage()) + "V");
    }

    protected void updateGPS() {
        TextView countGPS = (TextView) findViewById(R.id.valueSatellite);
        Gps gps = this.drone.getAttribute(AttributeType.GPS);
        countGPS.setText(String.format("%d", gps.getSatellitesCount()));
    }

    protected void updateAttitude() {
        TextView viewYaw = (TextView) findViewById(R.id.valueYAW);
        Attitude yaw = this.drone.getAttribute(AttributeType.ATTITUDE);
        float yaw_360 = (float) yaw.getYaw();
        if (yaw_360 < 0) {
            yaw_360 = 360 - Math.abs(yaw_360);
            if (yaw_360 == 360) {
                yaw_360 = 0;
            }
        }
        viewYaw.setText(String.format("%d", (int) yaw_360) + "deg");
        dronePosition.setAngle(yaw_360);
        //mLocationOverlay.setBearing(yaw_360);
    }

    protected void updateDronePosition() {
        dronePosition.setMap(null);
        Gps gps = this.drone.getAttribute(AttributeType.GPS);
        LatLong position = new LatLong(gps.getPosition());
        dronePosition.setPosition(new LatLng(position.getLatitude(), position.getLongitude()));
        mLocationCollection.add(new LatLng(position.getLatitude(), position.getLongitude()));
        if (mLocationCollection.size() > 2) {
            drawPolyLine();
        }
        dronePosition.setMap(mNaverMap);
        if (mCameraFix == true) {
            CameraUpdate cameraUpdate = CameraUpdate.scrollTo(new LatLng(position.getLatitude(), position.getLongitude()));
            mNaverMap.moveCamera(cameraUpdate);
        }
        //mLocationOverlay.setPosition(new LatLng(position.getLatitude(), position.getLongitude()));
    }

    protected void drawPolyLine() {
        mPolyline.setCoords(mLocationCollection);
        mPolyline.setCapType(PolylineOverlay.LineCap.Round);
        mPolyline.setJoinType(PolylineOverlay.LineJoin.Round);
        mPolyline.setColor(Color.WHITE);
        mPolyline.setMap(mNaverMap);
    }

    private void checkSoloState() {
        final SoloState soloState = drone.getAttribute(SoloAttributes.SOLO_STATE);
        if (soloState == null) {
            alertUser("Unable to retrieve the solo state.");
        } else {
            alertUser("Solo state is up to date.");
        }
    }

    @Override
    public void onDroneServiceInterrupted(String errorMsg) {
        addRecyclerViewText(errorMsg);
    }

    @Override
    public void onLinkStateUpdated(@NonNull LinkConnectionStatus connectionStatus) {

    }

    @Override
    public void onTowerConnected() {
        alertUser("DroneKit-Android Connected");
        this.controlTower.registerDrone(this.drone, this.handler);
        this.drone.registerDroneListener(this);
    }

    @Override
    public void onTowerDisconnected() {
        alertUser("DroneKit-Android Interrupted");
    }

    //Define Spinner

    public void onFlightModeSelected(View view) {
        VehicleMode vehicleMode = (VehicleMode) this.mModeSelector.getSelectedItem();

        VehicleApi.getApi(this.drone).setVehicleMode(vehicleMode, new AbstractCommandListener() {
            @Override
            public void onSuccess() {
                alertUser("Vehicle mode change successful.");
                addRecyclerViewText("?????? ?????? ??????");
            }

            @Override
            public void onError(int executionError) {
                alertUser("Vehicle mode change failed: " + executionError);
                addRecyclerViewText("?????? ?????? ??????");
            }

            @Override
            public void onTimeout() {
                alertUser("Vehicle mode change timed out.");
                addRecyclerViewText("?????? ?????? ?????? ??????");
            }
        });
    }

    //Define Tap BTN

    public void tapArmBTN(View view) {
        State vehicleState = this.drone.getAttribute(AttributeType.STATE);
        AlertDialog.Builder myAlertBuilder = new AlertDialog.Builder(MainActivity.this);
        if (vehicleState.isFlying()) {
            // Land
            VehicleApi.getApi(this.drone).setVehicleMode(VehicleMode.COPTER_LAND, new SimpleCommandListener() {
                @Override
                public void onSuccess() {
                    alertUser("?????? ???");
                    addRecyclerViewText("?????? ??????");
                }

                @Override
                public void onError(int executionError) {
                    alertUser("Unable to land the vehicle.");
                }

                @Override
                public void onTimeout() {
                    alertUser("Unable to land the vehicle.");
                }
            });
        } else if (vehicleState.isArmed()) {
            // Take off
            myAlertBuilder.setTitle("??????");
            myAlertBuilder.setMessage("????????? ???????????? ?????????????????????????");
            myAlertBuilder.setPositiveButton("??????", new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int which) {
                    ControlApi.getApi(drone).takeoff(mAltitude, new AbstractCommandListener() {
                        @Override
                        public void onSuccess() {
                            alertUser("?????? ???");
                            addRecyclerViewText("?????? ??????");
                        }

                        @Override
                        public void onError(int i) {
                            alertUser("????????? ??? ????????????.");
                        }

                        @Override
                        public void onTimeout() {
                            alertUser("?????? ??????????????? ?????????????????????.");
                        }
                    });
                }
            });
            myAlertBuilder.setNegativeButton("??????", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    Toast.makeText(getApplicationContext(), "?????????????????????",
                            Toast.LENGTH_SHORT).show();
                }
            });
            myAlertBuilder.show();
        } else {
            // Connected but not Armed
            myAlertBuilder.setTitle("????????????");
            myAlertBuilder.setMessage("????????? ????????? ???????????????????");
            myAlertBuilder.setPositiveButton("??????", new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int which) {
                    VehicleApi.getApi(drone).arm(true, false, new SimpleCommandListener() {
                        @Override
                        public void onSuccess() {
                            alertUser("?????? ??????");
                            addRecyclerViewText("?????? ??????");
                        }

                        @Override
                        public void onError(int executionError) {
                            alertUser("????????? ??? ??? ????????????.");
                        }

                        @Override
                        public void onTimeout() {
                            alertUser("?????? ??????????????? ?????????????????????");
                        }
                    });
                }
            });
            myAlertBuilder.setNegativeButton("??????", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    Toast.makeText(getApplicationContext(), "?????????????????????",
                            Toast.LENGTH_SHORT).show();
                }
            });
            myAlertBuilder.show();
        }
    }

    protected void updateArmButton() {
        State vehicleState = this.drone.getAttribute(AttributeType.STATE);
        Button armButton = (Button) findViewById(R.id.ARMBtn);

        if (!this.drone.isConnected()) {
            armButton.setVisibility(View.INVISIBLE);
        } else {
            armButton.setVisibility(View.VISIBLE);
        }

        if (vehicleState.isFlying()) {
            // Land
            armButton.setText("LAND");
        } else if (vehicleState.isArmed()) {
            // Take off
            armButton.setText("TAKE OFF");
        } else if (vehicleState.isConnected()) {
            // Connected but not Armed
            armButton.setText("ARM");
        }
    }

    public void tapConnectBTN(View view) {
        if (this.drone.isConnected()) {
            this.drone.disconnect();
        } else {
            /*
            ConnectionParameter connectionParams
                    = ConnectionParameter.newUdpConnection(null);
            this.drone.connect(connectionParams);

             */
            this.drone.connect(connParams);
        }

    }

    public void tapABBTN(View view) {
        Button addAB = (Button) findViewById(R.id.addAB);
        if (addAB.getVisibility() == view.VISIBLE) {
            doBtnInvisible("addAB", "subAB");
        } else {
            doBtnVisible("addAB", "subAB");
        }
    }

    public void tapAddABDistanceBTN(View view) {
        if (this.mABdistance < 100) this.mABdistance = this.mABdistance + 10;
        Button altitude = (Button) findViewById(R.id.ABView);
        altitude.setText("AB?????? " + mABdistance + "M");
    }

    public void tapSubABDistanceBTN(View view) {
        if (this.mABdistance > 10) this.mABdistance = this.mABdistance - 10;
        Button altitude = (Button) findViewById(R.id.ABView);
        altitude.setText("AB?????? " + mABdistance + "M");
    }

    public void tapFlightWidthBTN(View view) {
        Button addFlightWidth = (Button) findViewById(R.id.addFlightWidth);
        if (addFlightWidth.getVisibility() == view.VISIBLE) {
            doBtnInvisible("addFlightWidth", "subFlightWidth");
        } else {
            doBtnVisible("addFlightWidth", "subFlightWidth");
        }
    }

    public void tapAddFlightWidthBTN(View view) {
        if (this.mFlightwidth < 10) {
            this.mFlightwidth = this.mFlightwidth + 0.5;
        }
        Button altitude = (Button) findViewById(R.id.viewFlightWidth);
        altitude.setText("????????? " + mFlightwidth + "M");
    }

    public void tapSubFlightWidthBTN(View view) {
        if (this.mFlightwidth > 1) {
            this.mFlightwidth = this.mFlightwidth - 0.5;
        }
        Button altitude = (Button) findViewById(R.id.viewFlightWidth);
        altitude.setText("????????? " + mFlightwidth + "M");
    }

    public void tapAltitudeBTN(View view) {
        Button addAltitude = (Button) findViewById(R.id.addAltitude);
        if (addAltitude.getVisibility() == view.VISIBLE) {
            doBtnInvisible("addAltitude", "subAltitude");
        } else {
            doBtnVisible("addAltitude", "subAltitude");
        }
    }

    public void tapAddAltitudeBTN(View view) {
        if (mAltitude < 10.0) {
            this.mAltitude = mAltitude + 0.5;
        }
        Button altitude = (Button) findViewById(R.id.viewAltitude);
        altitude.setText("???????????? " + mAltitude + "M");
    }

    public void tapSubAltitudeBTN(View view) {
        if (mAltitude > 3.0) {
            this.mAltitude = mAltitude - 0.5;
        }
        Button altitude = (Button) findViewById(R.id.viewAltitude);
        altitude.setText("???????????? " + mAltitude + "M");
    }

    public void tapMissonBTN(View view) {
        doBtnVisible("ABMisson", "polygonMisson", "cancle");
    }

    public void tapChooseMissonABBTN(View view) {
        doBtnInvisible("ABMisson", "polygonMisson", "cancle");
        Button altitude = (Button) findViewById(R.id.viewMisson);
        altitude.setText("AB");
    }

    public void tapChooseMissonPolygonBTN(View view) {
        doBtnInvisible("ABMisson", "polygonMisson", "cancle");
        Button altitude = (Button) findViewById(R.id.viewMisson);
        altitude.setText("?????????");
    }

    public void tapCancleChooseMissonBTN(View view) {
        doBtnInvisible("ABMisson", "polygonMisson", "cancle");
        Button altitude = (Button) findViewById(R.id.viewMisson);
        altitude.setText("??????");
    }

    public void tapSetMapOptionBTN(View view) {
        doBtnVisible("mapSatellite", "mapTerrain", "mapBasic");
    }

    public void tapSetMapSatelliteBTN(View view) {
        mNaverMap.setMapType(NaverMap.MapType.Satellite);
        doBtnInvisible("mapSatellite", "mapTerrain", "mapBasic");
        Button mapView = (Button) findViewById(R.id.mapView);
        mapView.setText("????????????");
    }

    public void tapSetMapTerrainBTN(View view) {
        mNaverMap.setMapType(NaverMap.MapType.Terrain);
        doBtnInvisible("mapSatellite", "mapTerrain", "mapBasic");
        Button mapView = (Button) findViewById(R.id.mapView);
        mapView.setText("?????????");
    }

    public void tapSetMapBasicBTN(View view) {
        mNaverMap.setMapType(NaverMap.MapType.Basic);
        doBtnInvisible("mapSatellite", "mapTerrain", "mapBasic");
        Button mapView = (Button) findViewById(R.id.mapView);
        mapView.setText("????????????");
    }

    public void tapSetCadastralBTN(View view) {
        doBtnVisible("cadastralOn", "cadastralOff");
    }

    public void tapSetCadastralOnBTN(View view) {
        mNaverMap.setLayerGroupEnabled(NaverMap.LAYER_GROUP_CADASTRAL, true);
        doBtnInvisible("cadastralOn", "cadastralOff");
        Button cadastralView = (Button) findViewById(R.id.cadastralView);
        cadastralView.setText("?????????On");
    }

    public void tapSetCadastralOffBTN(View view) {
        mNaverMap.setLayerGroupEnabled(NaverMap.LAYER_GROUP_CADASTRAL, false);
        doBtnInvisible("cadastralOn", "cadastralOff");
        Button cadastralView = (Button) findViewById(R.id.cadastralView);
        cadastralView.setText("?????????Off");
    }

    public void tapSetCameraViewBTN(View view) {
        doBtnVisible("cameraMove", "mCameraFix");
    }

    public void tapSetCameraMoveBTN(View view) {
        mCameraFix = false;
        doBtnInvisible("cameraMove", "mCameraFix");
        Button cameraView = (Button) findViewById(R.id.cameraView);
        cameraView.setText("??? ??????");
    }

    public void tapSetCameraFixBTN(View view) {
        mCameraFix = true;
        doBtnInvisible("cameraMove", "mCameraFix");
        Button cameraView = (Button) findViewById(R.id.cameraView);
        cameraView.setText("??? ??????");
    }

    public void tapClearBtn(View view) {
        mPolyline.setMap(null);
        mLocationCollection.clear();
        mTextInRecycleerView.clear();
        recyclerViewText adapter = new recyclerViewText(mTextInRecycleerView);
        mRecyclerView.setAdapter(adapter);
    }

    public void tapTestBTN(View view) {
            /*
            ConnectionParameter connectionParams
                    = ConnectionParameter.newUdpConnection(null);
            this.drone.connect(connectionParams);

             */
        DroidPlannerPrefs mPrefs = DroidPlannerPrefs.getInstance(getApplicationContext());

        String btAddress = mPrefs.getBluetoothDeviceAddress();
        final @ConnectionType.Type int connectionType = mPrefs.getConnectionParameterType();

        Uri tlogLoggingUri = TLogUtils.getTLogLoggingUri(
                getApplicationContext(), connectionType, System.currentTimeMillis());

        final long EVENTS_DISPATCHING_PERIOD = 200L; //MS

        if (TextUtils.isEmpty(btAddress)) {
            connParams = null;
            startActivity(new Intent(getApplicationContext(), BluetoothDevicesActivity.class).
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } else {
            connParams = ConnectionParameter.newBluetoothConnection(btAddress,
                    tlogLoggingUri, EVENTS_DISPATCHING_PERIOD);
        }

    }


    //?????????????????? ??????

    public void addRecyclerViewText(String msg) {
        mTextInRecycleerView.add(msg);
        // ????????????????????? ?????? ??????.
        recyclerViewText adapter = new recyclerViewText(mTextInRecycleerView);
        mRecyclerView.setAdapter(adapter);
    }

    // Helper methods
    // ==========================================================

    protected void alertUser(String message) {
        Toast.makeText(getApplicationContext(), message, Toast.LENGTH_SHORT).show();
        Log.d(TAG, message);
    }

    private void runOnMainThread(Runnable runnable) {
        mainHandler.post(runnable);
    }

    protected void doBtnVisible(String... strings) {
        for (int i = 0; i < strings.length; i++) {
            Button button = findViewById(getResources().getIdentifier(strings[i], "id", "com.LSH.mygcs"));
            button.setVisibility(View.VISIBLE);
        }
    }

    protected void doBtnInvisible(String... strings) {
        for (int i = 0; i < strings.length; i++) {
            Button button = findViewById(getResources().getIdentifier(strings[i], "id", "com.LSH.mygcs"));
            button.setVisibility(View.INVISIBLE);
        }
    }

}

class GuideMode {
    //static LatLng mGuidedPoint; //??????????????? ????????? ??????
    //static Marker mMarkerGuide = new com.naver.maps.map.overlay.Marker(); //GCS ?????? ???????????? ??????
    //static OverlayImage guideIcon = OverlayImage.fromResource(R.drawable.marker_guide);
    static Boolean DialogSimple(final Drone drone, final LatLong point, Context main) {
        State vehicleState = drone.getAttribute(AttributeType.STATE);
        if (vehicleState.isFlying()) {
            State vehiclemode = drone.getAttribute(AttributeType.STATE);
            VehicleMode dronemode = vehiclemode.getVehicleMode();
            if (dronemode == VehicleMode.COPTER_GUIDED) {
                AlertDialog.Builder alt_bld = new AlertDialog.Builder(main);
                alt_bld.setMessage("??????????????? ?????????????????? ????????? ????????? ???????????????.").setCancelable(false).
                        setPositiveButton("??????", new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int id) {
                                // Action for 'Yes' Button
                                VehicleApi.getApi(drone).setVehicleMode(VehicleMode.COPTER_GUIDED,
                                        new AbstractCommandListener() {
                                            @Override
                                            public void onSuccess() {
                                                ControlApi.getApi(drone).goTo(point, true, null);
                                            }

                                            @Override
                                            public void onError(int i) {

                                            }

                                            @Override
                                            public void onTimeout() {

                                            }
                                        });
                            }
                        }).setNegativeButton("??????", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        dialog.cancel();
                    }
                });
                AlertDialog alert = alt_bld.create();
                // Title for AlertDialog
                alert.setTitle("????????? ??????");
                // Icon for AlertDialog
                //alert.setIcon(R.drawable.drone);
                alert.show();
            } else {
                ControlApi.getApi(drone).goTo(point, true, null);
            }
            while (CheckGoal(drone, new LatLng(point.getLatitude(), point.getLongitude())) == false) {
            }
            VehicleApi.getApi(drone).setVehicleMode(VehicleMode.COPTER_LOITER);
            return true;
        }
        return false;
    }

    public static boolean CheckGoal(final Drone drone, LatLng recentLatLng) {
        GuidedState guidedState = drone.getAttribute(AttributeType.GUIDED_STATE);
        LatLng target = new LatLng(guidedState.getCoordinate().
                getLatitude(), guidedState.getCoordinate().getLongitude());
        return target.distanceTo(recentLatLng) <= 1;
    }
}

class recyclerViewText extends RecyclerView.Adapter<recyclerViewText.ViewHolder> {

    private ArrayList<String> mData = null;

    // ????????? ?????? ???????????? ????????? ?????????.
    public class ViewHolder extends RecyclerView.ViewHolder {
        TextView textView1;

        ViewHolder(View itemView) {
            super(itemView);

            // ??? ????????? ?????? ??????. (hold strong reference)
            textView1 = itemView.findViewById(R.id.text);
        }
    }

    // ??????????????? ????????? ????????? ????????? ????????????.
    recyclerViewText(ArrayList<String> list) {
        mData = list;
    }

    // onCreateViewHolder() - ????????? ?????? ?????? ????????? ?????? ???????????? ??????.
    @Override
    public recyclerViewText.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        Context context = parent.getContext();
        LayoutInflater inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);

        View view = inflater.inflate(R.layout.item_recycler, parent, false);
        recyclerViewText.ViewHolder vh = new recyclerViewText.ViewHolder(view);

        return vh;
    }

    // onBindViewHolder() - position??? ???????????? ???????????? ???????????? ??????????????? ??????.
    @Override
    public void onBindViewHolder(recyclerViewText.ViewHolder holder, int position) {
        String text = mData.get(position);
        holder.textView1.setText(text);
    }

    // getItemCount() - ?????? ????????? ?????? ??????.
    @Override
    public int getItemCount() {
        return mData.size();
    }
}
