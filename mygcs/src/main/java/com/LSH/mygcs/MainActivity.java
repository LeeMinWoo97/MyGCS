package com.LSH.mygcs;

import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Color;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.naver.maps.geometry.LatLng;
import com.naver.maps.map.CameraUpdate;
import com.naver.maps.map.LocationTrackingMode;
import com.naver.maps.map.MapFragment;
import com.naver.maps.map.NaverMap;
import com.naver.maps.map.OnMapReadyCallback;
import com.naver.maps.map.UiSettings;
import com.naver.maps.map.overlay.LocationOverlay;
import com.naver.maps.map.overlay.Marker;
import com.naver.maps.map.overlay.OverlayImage;
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
import com.o3dr.services.android.lib.drone.mission.item.command.YawCondition;
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

import java.text.AttributedCharacterIterator;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity implements DroneListener, TowerListener, LinkListener, OnMapReadyCallback {

    private static final String TAG = MainActivity.class.getSimpleName();
    private NaverMap mNaverMap;
    private MapFragment mMapFragment;
    private LocationOverlay mLocationOverlay;
    private Spinner mModeSelector;
    private static final int LOCATION_PERMISSION_REQUEST_CODE = 1000;
    private FusedLocationSource locationSource;
    private Marker dronePosition = new Marker();
    private Boolean cameraFix = false;
    private ArrayList<LatLng> locationCollection = new ArrayList<LatLng>();
    private PolylineOverlay polyline = new PolylineOverlay();
    private Drone drone;
    private int droneType = Type.TYPE_UNKNOWN;
    private ControlTower controlTower;
    private final Handler handler = new Handler();
    private double mAltitude = 3.0;
    private double flightwidth = 3.0;
    private int ABdistance = 30;
    private static final int DEFAULT_UDP_PORT = 14550;
    private static final int DEFAULT_USB_BAUD_RATE = 57600;

    //private Spinner modeSelector;

    Handler mainHandler;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        locationSource =
                new FusedLocationSource(this, LOCATION_PERMISSION_REQUEST_CODE);

        FragmentManager fm = getSupportFragmentManager();
        mMapFragment = (MapFragment)fm.findFragmentById(R.id.map);
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
        naverMap.setLocationSource(locationSource);
        naverMap.setLocationTrackingMode(LocationTrackingMode.NoFollow);
        naverMap.setOnMapLongClickListener((point, coord) ->{
            target.setPosition(new LatLng(coord.latitude,coord.longitude));
            target.setMap(naverMap);
            guidemode.DialogSimple(drone,new LatLong(coord.latitude,coord.longitude),this);
            target.setMap(null);
            }
        );
    }


    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,  @NonNull int[] grantResults) {
        if (locationSource.onRequestPermissionsResult(
                requestCode, permissions, grantResults)) {
            if (!locationSource.isActivated()) { // 권한 거부됨
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

    public void onBtnConnectTap(View view) {
        if (this.drone.isConnected()) {
            this.drone.disconnect();
        } else {
            ConnectionParameter connectionParams
                    = ConnectionParameter.newUdpConnection(null);
            this.drone.connect(connectionParams);
        }

    }

    public void onFlightModeSelected(View view) {
        VehicleMode vehicleMode = (VehicleMode) this.mModeSelector.getSelectedItem();

        VehicleApi.getApi(this.drone).setVehicleMode(vehicleMode, new AbstractCommandListener() {
            @Override
            public void onSuccess() {
                alertUser("Vehicle mode change successful.");
            }

            @Override
            public void onError(int executionError) {
                alertUser("Vehicle mode change failed: " + executionError);
            }

            @Override
            public void onTimeout() {
                alertUser("Vehicle mode change timed out.");
            }
        });
    }

    @Override
    public void onDroneEvent(String event, Bundle extras) {
        Button connetcButton = (Button) findViewById(R.id.connectBtn);
        switch (event) {
            case AttributeEvent.STATE_CONNECTED:
                alertUser("Drone Connected");
                connetcButton.setText("Disconnect");
/*
                updateConnectedButton(this.drone.isConnected());
                updateArmButton();
 */
                checkSoloState();

                break;

            case AttributeEvent.STATE_DISCONNECTED:
                alertUser("Drone Disconnected");
                connetcButton.setText("Connect");
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

            case AttributeEvent.GPS_COUNT:
                countGPS();

            case AttributeEvent.ATTITUDE_UPDATED:
                updateAttitude();

            case AttributeEvent.GPS_POSITION:
                updateGps();

            default:
                // Log.i("DRONE_EVENT", event); //Uncomment to see events from the drone
                break;
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

    public void armButtonClick(View view) {
        State vehicleState = this.drone.getAttribute(AttributeType.STATE);
        AlertDialog.Builder myAlertBuilder =
                new AlertDialog.Builder(MainActivity.this);
        if (vehicleState.isFlying()) {
            // Land
            VehicleApi.getApi(this.drone).setVehicleMode(VehicleMode.COPTER_LAND, new SimpleCommandListener() {
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
            myAlertBuilder.setTitle("이륙");
            myAlertBuilder.setMessage("지정한 고도까지 이륙하시겠습니까?");
            myAlertBuilder.setPositiveButton("확인",new DialogInterface.OnClickListener(){
                public void onClick(DialogInterface dialog, int which){
                    ControlApi.getApi(drone).takeoff(mAltitude, new AbstractCommandListener() {
                        @Override
                        public void onSuccess() {
                            alertUser("이륙 중");
                        }
                        @Override
                        public void onError(int i) {
                            alertUser("이륙할 수 없습니다.");
                        }
                        @Override
                        public void onTimeout() {
                            alertUser("이륙 준비시간이 초과되었습니다.");
                        }
                    });
                }
            });
            myAlertBuilder.setNegativeButton("취소", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    Toast.makeText(getApplicationContext(),"취소되었습니다",
                            Toast.LENGTH_SHORT).show();
                }
            });
            myAlertBuilder.show();
        }
        else {
            // Connected but not Armed
            myAlertBuilder.setTitle("모터시동");
            myAlertBuilder.setMessage("모터에 시동을 거시겠습니까?");
            myAlertBuilder.setPositiveButton("확인",new DialogInterface.OnClickListener(){
                public void onClick(DialogInterface dialog, int which){
                    VehicleApi.getApi(drone).arm(true, false, new SimpleCommandListener() {
                        @Override
                        public void onError(int executionError) {
                            alertUser("시동을 걸 수 없습니다.");
                        }

                        @Override
                        public void onTimeout() {
                            alertUser("시동 준비시간이 초과되었습니다");
                        }
                    });
                }
            });
            myAlertBuilder.setNegativeButton("취소", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    Toast.makeText(getApplicationContext(),"취소되었습니다",
                            Toast.LENGTH_SHORT).show();
                }
            });
            myAlertBuilder.show();
        }
    }

    protected void updateVehicleModesForType(int droneType) {
        List<VehicleMode> vehicleModes = VehicleMode.getVehicleModePerDroneType(droneType);
        ArrayAdapter<VehicleMode> vehicleModeArrayAdapter = new ArrayAdapter<VehicleMode>(this, android.R.layout.simple_spinner_item, vehicleModes);
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

    protected void updateBattery(){
        TextView batteryView = (TextView) findViewById(R.id.valueVolt);
        Battery droneBattery = this.drone.getAttribute(AttributeType.BATTERY);
        batteryView.setText(String.format("%3.1f", droneBattery.getBatteryVoltage()) + "V");
    }

    protected void countGPS(){
        TextView countGPS = (TextView) findViewById(R.id.valueSatellite);
        Gps gps = this.drone.getAttribute(AttributeType.GPS);
        countGPS.setText(String.format("%d", gps.getSatellitesCount()));
    }

    protected void updateAttitude(){
        TextView viewYaw = (TextView) findViewById(R.id.valueYAW);
        Attitude yaw = this.drone.getAttribute(AttributeType.ATTITUDE);
        int yaw_360 = (int) yaw.getYaw();
        if(yaw_360 < 0){
            yaw_360 = 360 - Math.abs(yaw_360);
            if(yaw_360 == 360) yaw_360 = 0;
        }
        viewYaw.setText(String.format("%d", yaw_360 ) + "deg");
        //mLocationOverlay.setBearing(yaw_360);
    }

    protected void updateGps(){
        dronePosition.setMap(null);
        Gps gps = this.drone.getAttribute(AttributeType.GPS);
        LatLong position = new LatLong(gps.getPosition());
        dronePosition.setPosition(new LatLng(position.getLatitude(),position.getLongitude()));
        locationCollection.add(new LatLng(position.getLatitude(),position.getLongitude()));
        if(locationCollection.size() > 2) drawPolyLine();
        dronePosition.setMap(mNaverMap);
        if(cameraFix == true){
            CameraUpdate cameraUpdate = CameraUpdate.scrollTo(new LatLng(position.getLatitude(),position.getLongitude()));
            mNaverMap.moveCamera(cameraUpdate);
        }
        //mLocationOverlay.setPosition(new LatLng(position.getLatitude(), position.getLongitude()));
    }

    protected void drawPolyLine(){
        polyline.setCoords(locationCollection);
        polyline.setCapType(PolylineOverlay.LineCap.Round);
        polyline.setJoinType(PolylineOverlay.LineJoin.Round);
        polyline.setColor(Color.WHITE);
        polyline.setMap(mNaverMap);
    }

    private void checkSoloState() {
        final SoloState soloState = drone.getAttribute(SoloAttributes.SOLO_STATE);
        if (soloState == null){
            alertUser("Unable to retrieve the solo state.");
        }
        else {
            alertUser("Solo state is up to date.");
        }
    }

    @Override
    public void onDroneServiceInterrupted(String errorMsg) {

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

    public void tapAB(View view){
        doBtnVisible("addAB","subAB");
    }

    public void tapFlightWidth(View view){
        doBtnVisible("addFlightWidth","subFlightWidth");
    }

    public void tapAltitude(View view){
        doBtnVisible("addAltitude","subAltitude");
    }

    public void tapMisson(View view){
        doBtnVisible("ABMisson","polygonMisson","cancle");
    }

    public void addAltitude(View view){
        if(mAltitude < 10.0) this.mAltitude = mAltitude + 0.5;
        doBtnInvisible("addAltitude","subAltitude");
        Button altitude = (Button) findViewById(R.id.viewAltitude);
        altitude.setText("이륙고도 " + mAltitude + "M");
    }

    public void subAltitude(View view){
        if(mAltitude > 3.0) this.mAltitude = mAltitude - 0.5;
        doBtnInvisible("addAltitude","subAltitude");
        Button altitude = (Button) findViewById(R.id.viewAltitude);
        altitude.setText("이륙고도 " + mAltitude + "M");
    }

    public void addABDistance(View view){
        this.ABdistance = ABdistance + 10;
        doBtnInvisible("addAB","subAB");
        Button altitude = (Button) findViewById(R.id.ABView);
        altitude.setText("AB거리 " + ABdistance + "M");
    }

    public void subABDistance(View view){
        this.ABdistance = ABdistance - 10;
        doBtnInvisible("addAB","subAB");
        Button altitude = (Button) findViewById(R.id.ABView);
        altitude.setText("AB거리 " + ABdistance + "M");
    }

    public void addFlightWidth(View view){
        this.flightwidth = flightwidth + 0.5;
        doBtnInvisible("addFlightWidth","subFlightWidth");
        Button altitude = (Button) findViewById(R.id.viewFlightWidth);
        altitude.setText("비행폭 " + flightwidth + "M");
    }

    public void subFlightWidth(View view){
        this.flightwidth = flightwidth - 0.5;
        doBtnInvisible("addFlightWidth","subFlightWidth");
        Button altitude = (Button) findViewById(R.id.viewFlightWidth);
        altitude.setText("비행폭 " + flightwidth + "M");
    }

    public void chooseMissonAB(View view){
        doBtnInvisible("ABMisson","polygonMisson","cancle");
        Button altitude = (Button) findViewById(R.id.viewMisson);
        altitude.setText("AB");
    }

    public void chooseMissonPolygon(View view){
        doBtnInvisible("ABMisson","polygonMisson","cancle");
        Button altitude = (Button) findViewById(R.id.viewMisson);
        altitude.setText("다각형");
    }

    public void cancleChooseMisson(View view){
        doBtnInvisible("ABMisson","polygonMisson","cancle");
    }

    public void setMapOption(View view){
        doBtnVisible("mapSatellite","mapTerrain","mapBasic");
    }

    public void setCadastral(View view){
        doBtnVisible("cadastralOn","cadastralOff");
    }

    public void setCameraView(View view){
        doBtnVisible("cameraMove","cameraFix");
    }

    public void setMapSatellite(View view){
        mNaverMap.setMapType(NaverMap.MapType.Satellite);
        doBtnInvisible("mapSatellite","mapTerrain","mapBasic");
        Button mapView = (Button) findViewById(R.id.mapView);
        mapView.setText("위성지도");
    }

    public void setMapTerrain(View view){
        mNaverMap.setMapType(NaverMap.MapType.Terrain);
        doBtnInvisible("mapSatellite","mapTerrain","mapBasic");
        Button mapView = (Button) findViewById(R.id.mapView);
        mapView.setText("지형도");
    }

    public void setMapBasic(View view){
        mNaverMap.setMapType(NaverMap.MapType.Basic);
        doBtnInvisible("mapSatellite","mapTerrain","mapBasic");
        Button mapView = (Button) findViewById(R.id.mapView);
        mapView.setText("일반지도");
    }

    public void setCadastralOn(View view){
        mNaverMap.setLayerGroupEnabled(NaverMap.LAYER_GROUP_CADASTRAL, true);
        doBtnInvisible("cadastralOn","cadastralOff");
        Button cadastralView = (Button) findViewById(R.id.cadastralView);
        cadastralView.setText("지형도On");
    }

    public void setCadastralOff(View view){
        mNaverMap.setLayerGroupEnabled(NaverMap.LAYER_GROUP_CADASTRAL, false);
        doBtnInvisible("cadastralOn","cadastralOff");
        Button cadastralView = (Button) findViewById(R.id.cadastralView);
        cadastralView.setText("지형도Off");
    }

    public void setCameraMove(View view){
        cameraFix = false;
        doBtnInvisible("cameraMove","cameraFix");
        Button cameraView = (Button) findViewById(R.id.cameraView);
        cameraView.setText("맵 이동");
    }

    public void setCameraFix(View view){
        cameraFix = true;
        doBtnInvisible("cameraMove","cameraFix");
        Button cameraView = (Button) findViewById(R.id.cameraView);
        cameraView.setText("맵 잠금");
    }

    public void clickClearBtn(View view){
        polyline.setMap(null);
        locationCollection.clear();
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

    protected void doBtnVisible(String...strings){
        for(int i =0; i<strings.length; i++){
            Button button = findViewById(getResources().getIdentifier(strings[i], "id", "com.LSH.mygcs"));
            button.setVisibility(View.VISIBLE);
        }
    }

    protected void doBtnInvisible(String...strings){
        for(int i =0; i<strings.length; i++){
            Button button = findViewById(getResources().getIdentifier(strings[i], "id", "com.LSH.mygcs"));
            button.setVisibility(View.INVISIBLE);
        }
    }

}

class GuideMode {
    //static LatLng mGuidedPoint; //가이드모드 목적지 저장
    //static Marker mMarkerGuide = new com.naver.maps.map.overlay.Marker(); //GCS 위치 표시마커 옵션
    //static OverlayImage guideIcon = OverlayImage.fromResource(R.drawable.marker_guide);
    static void DialogSimple(final Drone drone, final LatLong point, Context main) {
        State vehicleState = drone.getAttribute(AttributeType.STATE);
        if (vehicleState.isFlying()) {
            AlertDialog.Builder alt_bld = new AlertDialog.Builder(main);
            alt_bld.setMessage("확인하시면 가이드모드로 전환후 기체가 이동합니다.").setCancelable(false).setPositiveButton("확인", new DialogInterface.OnClickListener() {
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
            }).setNegativeButton("취소", new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int id) {
                    dialog.cancel();
                }
            });
            AlertDialog alert = alt_bld.create();
            // Title for AlertDialog
            alert.setTitle("가이드 비행");
            // Icon for AlertDialog
            //alert.setIcon(R.drawable.drone);
            alert.show();
            while(CheckGoal(drone,new LatLng(point.getLatitude(),point.getLongitude())) == false){ }
            VehicleApi.getApi(drone).setVehicleMode(VehicleMode.COPTER_LOITER);
        }
    }

    public static boolean CheckGoal(final Drone drone, LatLng recentLatLng) {
        GuidedState guidedState = drone.getAttribute(AttributeType.GUIDED_STATE);
        LatLng target = new LatLng(guidedState.getCoordinate().getLatitude(),
                guidedState.getCoordinate().getLongitude());
        return target.distanceTo(recentLatLng) <= 1;
    }
}
