package com.wevois.wastebinmonitor;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.content.IntentSender;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.hardware.Camera;
import android.location.Geocoder;
import android.location.Location;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.common.api.ResolvableApiException;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.LocationSettingsRequest;
import com.google.android.gms.maps.model.LatLng;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.MutableData;
import com.google.firebase.database.Transaction;
import com.google.firebase.storage.StorageMetadata;
import com.google.firebase.storage.StorageReference;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.google.maps.android.PolyUtil;
import com.wevois.wastebinmonitor.ModelClasses.ModelForImages;
import com.wevois.wastebinmonitor.ModelClasses.ModelWastebinTypes;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

public class MainActivity extends AppCompatActivity {
    JSONObject boundariesData;
    DatabaseReference rootRef;
    Bitmap photo;
    SharedPreferences pref, backUpPref;
    boolean isTransferStation = false, isOpenDepo = false, isClean = false, isPass = true;
    StorageReference stoRef;
    String userId, lang;
    AlertDialog wasteOptionDialog, isCleanDialog, captureDialog, cngLangDialog;
    Camera mCamera;
    Camera.PictureCallback pictureCallback;
    AdapterForExpandableList adapterForExpandableList;
    CommonFunctions cmn = new CommonFunctions();
    List<ModelWastebinTypes> wasteBinTypesList = new ArrayList<>();
    List<ModelForImages> imagesList = new ArrayList<>();
    JSONObject data_to_upload = new JSONObject();
    HashMap<String, String> wasteBinTypesMap = new HashMap<>();
    LocationCallback locationCallback;
    Location lastKnownLocation;
    public static final int FOCUS_AREA_SIZE = 300;
    SimpleDateFormat input = new SimpleDateFormat("yyyy-MM-dd", Locale.US);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        setPageTitle();
        inIt();
    }

    @SuppressLint("SetTextI18n")
    private void setPageTitle() {
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        Objects.requireNonNull(getSupportActionBar()).setDisplayShowTitleEnabled(false);
    }

    private void inIt() {
        TextView helpLineTv = findViewById(R.id.helpline_tv);
        fetchWastebinTypes(MainActivity.this);
        cmn.fetchHelplineNumber(MainActivity.this, helpLineTv);
        runOnUiThread(this::fetchWardBoundariesData);
        rootRef = cmn.getDatabaseRef(this);
        pref = getSharedPreferences("LoginDetails", MODE_PRIVATE);
        backUpPref = getSharedPreferences("BackUp", MODE_PRIVATE);
        userId = pref.getString("uid", "");
        stoRef = cmn.getStoRef(MainActivity.this).child("WastebinMonitorImages/" + cmn.getYear() + "/" + cmn.getMonth() + "/" + cmn.getDate());
        ListView parentLv = findViewById(R.id.parent_list);
        adapterForExpandableList = new AdapterForExpandableList();
        parentLv.setAdapter(adapterForExpandableList);

        getPathForImages();
        attachListener();
        findViewById(R.id.capture_wastebin_image_btn).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (isPass) {
                    isPass = false;
                    cmn.setProgressDialog("", "Please wait", MainActivity.this, MainActivity.this);
                    checkImageSto();
                }
            }
        });

    }

    private void checkImageSto() {
        Log.d("TAG", "checkImageSto:A ");
        if (backUpPref.getString("imageName", null) != null) {

            StorageMetadata metadata = new StorageMetadata.Builder().setContentType("json").build();

            stoRef.child(backUpPref.getString("imageNameFirebase", null))
                    .putFile(Uri.fromFile(getFile(backUpPref.getString("imageName", null))), metadata)
                    .addOnCompleteListener(task -> {
                        if (task.isSuccessful()) {
                            backUpPref.edit().remove("imageName").apply();
                            checkDataRDMS();
                        }
                    });

        } else {
            checkDataRDMS();
        }
    }

    private void checkDataRDMS() {
        Log.d("TAG", "checkImageSto:B ");
        if (backUpPref.getString("data", null) != null) {

            try {
                JSONObject obj = new JSONObject(backUpPref.getString("data", null));
                rootRef.child("WastebinMonitor/ImagesData/" + cmn.getYear() + "/" + cmn.getMonth() + "/" + cmn.getDate() + "/" + backUpPref.getString("cat", null) + "/" + backUpPref.getInt("key", 0))
                        .setValue(new Gson().fromJson(obj.toString(), new TypeToken<HashMap<String, Object>>() {
                        }.getType()))
                        .addOnCompleteListener(task1 -> {
                            backUpPref.edit().remove("data").apply();
                            checkImageRDMS();
                        });


            } catch (JSONException e) {
                e.printStackTrace();
            }

        } else {
            checkImageRDMS();
        }
    }

    private void checkImageRDMS() {
        Log.d("TAG", "checkImageSto:C ");
        if (backUpPref.getString("imageNameFirebase", null) != null) {

            rootRef.child("WastebinMonitor/UserImageRef/" + userId).push().setValue(backUpPref.getString("imageNameFirebase", null))
                    .addOnCompleteListener(task1 -> {
                        backUpPref.edit().remove("imageNameFirebase").apply();
                        DateWiseTotalCount();
                    });

        } else {
            DateWiseTotalCount();
        }
    }

    private void DateWiseTotalCount() {
        Log.d("TAG", "checkImageSto:D ");
        if (backUpPref.getBoolean("DateWiseTotalCount", false)) {

            rootRef.child("WastebinMonitor/Summary/DateWise/" + cmn.getDate() + "/totalCount")
                    .runTransaction(new Transaction.Handler() {
                        @NonNull
                        @Override
                        public Transaction.Result doTransaction(@NonNull MutableData currentData) {
                            if (currentData.getValue() == null) {
                                currentData.setValue(1);
                            } else {
                                currentData.setValue(String.valueOf((Integer.parseInt(currentData.getValue().toString()) + 1)));
                            }
                            return Transaction.success(currentData);
                        }

                        @Override
                        public void onComplete(@Nullable DatabaseError error, boolean committed, @Nullable DataSnapshot currentData) {
                            if (error == null) {
                                backUpPref.edit().putBoolean("DateWiseTotalCount", false).apply();
                                CategoryTotalCount();
                            }
                        }
                    });
        } else {
            CategoryTotalCount();
        }
    }

    private void CategoryTotalCount() {
        Log.d("TAG", "checkImageSto:E ");
        if (backUpPref.getBoolean("CategoryTotalCount", false)) {

            rootRef.child("WastebinMonitor/Summary/CategoryWise/totalCount")
                    .runTransaction(new Transaction.Handler() {
                        @NonNull
                        @Override
                        public Transaction.Result doTransaction(@NonNull MutableData currentData) {
                            if (currentData.getValue() == null) {
                                currentData.setValue(1);
                            } else {
                                currentData.setValue(String.valueOf((Integer.parseInt(currentData.getValue().toString()) + 1)));
                            }
                            return Transaction.success(currentData);
                        }

                        @Override
                        public void onComplete(@Nullable DatabaseError error, boolean committed, @Nullable DataSnapshot currentData) {
                            if (error == null) {
                                backUpPref.edit().putBoolean("CategoryTotalCount", false).apply();
                                DateWiseCatTotalCount();
                            }
                        }
                    });
        } else {
            DateWiseCatTotalCount();
        }
    }

    private void DateWiseCatTotalCount() {
        Log.d("TAG", "checkImageSto:f ");
        if (backUpPref.getBoolean("DateWiseCatTotalCount", false)) {

            rootRef.child("WastebinMonitor/Summary/DateWise/" + cmn.getDate() + "/" + backUpPref.getString("cat", null) + "/totalCount")
                    .runTransaction(new Transaction.Handler() {
                        @NonNull
                        @Override
                        public Transaction.Result doTransaction(@NonNull MutableData currentData) {
                            if (currentData.getValue() == null) {
                                currentData.setValue(1);
                            } else {
                                currentData.setValue(String.valueOf((Integer.parseInt(currentData.getValue().toString()) + 1)));
                            }
                            return Transaction.success(currentData);
                        }

                        @Override
                        public void onComplete(@Nullable DatabaseError error, boolean committed, @Nullable DataSnapshot currentData) {
                            if (error == null) {
                                backUpPref.edit().putBoolean("DateWiseCatTotalCount", false).apply();
                                CategoryWiseTotalCount();
                            }
                        }
                    });
        } else {
            CategoryWiseTotalCount();
        }
    }

    private void CategoryWiseTotalCount() {
        Log.d("TAG", "checkImageSto:G ");
        if (backUpPref.getBoolean("CategoryWiseTotalCount", false)) {
            rootRef.child("WastebinMonitor/Summary/CategoryWise/" + backUpPref.getString("cat", null) + "/totalCount")
                    .runTransaction(new Transaction.Handler() {
                        @NonNull
                        @Override
                        public Transaction.Result doTransaction(@NonNull MutableData currentData) {
                            if (currentData.getValue() == null) {
                                currentData.setValue(1);
                            } else {
                                currentData.setValue(String.valueOf((Integer.parseInt(currentData.getValue().toString()) + 1)));
                            }
                            return Transaction.success(currentData);
                        }

                        @Override
                        public void onComplete(@Nullable DatabaseError error, boolean committed, @Nullable DataSnapshot currentData) {
                            if (error == null) {
                                backUpPref.edit().putBoolean("CategoryWiseTotalCount", false).apply();
                                checkGps();
                            }
                        }
                    });
        } else {
            checkGps();
        }
    }

    private void checkGps() {
        LocationServices.getSettingsClient(this).checkLocationSettings(new LocationSettingsRequest.Builder()
                .addLocationRequest(new LocationRequest().setInterval(5000).setFastestInterval(1000).setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY))
                .setAlwaysShow(true).setNeedBle(true).build())
                .addOnCompleteListener(task1 -> {
                    try {
                        task1.getResult(ApiException.class);
                        if (task1.isSuccessful()) {
                            wasteBinOptionsDialog();
                        }
                    } catch (ApiException e) {
                        if (e instanceof ResolvableApiException) {
                            try {
                                ResolvableApiException resolvable = (ResolvableApiException) e;
                                resolvable.startResolutionForResult(this, 0002);
                            } catch (IntentSender.SendIntentException ex) {
                                ex.printStackTrace();
                            }
                        }
                    }
                });
    }

    private void attachListener() {
        if (ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(@NonNull LocationResult locationResult) {
                super.onLocationResult(locationResult);
                if (locationResult.getLocations().size() > 0) {
                    lastKnownLocation = locationResult.getLastLocation();
                }
            }
        };
        LocationRequest locationRequest = new LocationRequest().setInterval(1000).setFastestInterval(1000).setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
        LocationServices.getFusedLocationProviderClient(this).requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper());
    }

    @Override
    protected void onDestroy() {
        if (locationCallback != null) {
            LocationServices.getFusedLocationProviderClient(this).removeLocationUpdates(locationCallback);
        }
        super.onDestroy();
    }

    public void prepareDS() {
        try {
            String content;
            lang = pref.getString("lang", "en");
            if (lang.equals("en")) {
                content = pref.getString("wastebin_types_en", "");
            } else {
                content = pref.getString("wastebin_types_hi", "");
            }
            boolean handleExpandableListBool = true;
            for (String str : new ArrayList<>(Arrays.asList(content.substring(1, (content.length() - 1)).split(",")))) {
                String[] wastebinTypeArray = str.split("~");
                wasteBinTypesMap.put(wastebinTypeArray[1].trim(), wastebinTypeArray[0].trim());
                wasteBinTypesList.add(new ModelWastebinTypes(wastebinTypeArray[1].trim(), handleExpandableListBool));
                if (handleExpandableListBool) handleExpandableListBool = false;
            }
            adapterForExpandableList.notifyDataSetChanged();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @SuppressLint("StaticFieldLeak")
    private void setCaptureBtn() {
        new AsyncTask<Void, Void, Boolean>() {
            @Override
            protected void onPreExecute() {
                super.onPreExecute();
                cmn.setProgressDialog("", "Please wait...", MainActivity.this, MainActivity.this);
            }

            @Override
            protected Boolean doInBackground(Void... p) {
                return cmn.network(MainActivity.this);
            }

            @Override
            protected void onPostExecute(Boolean result) {

                if (result) {
                    mCamera.takePicture(null, null, null, pictureCallback);
                } else {
                    cmn.closeDialog(MainActivity.this);
                    AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
                    builder.setMessage("Internet not Available")
                            .setCancelable(false)
                            .setPositiveButton("Retry", (dialog, id) -> {
                                isPass = true;
                                dialog.cancel();
                            })
                            .setNegativeButton("Exit", (dialog, i) -> finish());
                    AlertDialog alertDialog = builder.create();
                    alertDialog.show();
                }
            }
        }.execute();
    }

    private void getPathForImages() {
        try {
            ContextWrapper cw = new ContextWrapper(getApplicationContext());
            File directory = cw.getDir("WastebinImages", Context.MODE_PRIVATE);
            Date endDate = input.parse(input.format(new Date()));
            long daysInMilli = 1000 * 60 * 60 * 24;
            int limit = cmn.getDatabaseSp(this).getInt("DaysForDeletingImagesInWastebinMonitor", 10);
            boolean isDelete = cmn.getDatabaseSp(this).getBoolean("isDeleteImages", true);
            for (File file : Objects.requireNonNull(directory.listFiles())) {
                String[] str = file.getName().split("~");
                if (isDelete) {
                    cmn.getDatabaseSp(this).edit().putBoolean("isDeleteImages", false).apply();
                    Date startDate = input.parse(str[1]);
                    long different = endDate.getTime() - startDate.getTime();
                    long days = different / daysInMilli;
                    if (days > limit) {
                        file.delete();
                        continue;
                    }
                }
                imagesList.add(new ModelForImages(str[1], str[2], Integer.parseInt(str[3]), file));
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    private void wasteBinOptionsDialog() {
        try {
            View diaLayout = MainActivity.this.getLayoutInflater().inflate(R.layout.dialog_for_image_options, null);
            AlertDialog.Builder alertDialog = new AlertDialog.Builder(MainActivity.this).setView(diaLayout).setCancelable(false);
            wasteOptionDialog = alertDialog.create();
            GridView iOLV = diaLayout.findViewById(R.id.image_options_listview);

            iOLV.setAdapter(new AdapterForImageOptionsList());

            diaLayout.findViewById(R.id.close_dialog).setOnClickListener(v -> {
                isPass = true;
                wasteOptionDialog.dismiss();
            });
            wasteOptionDialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            cmn.closeDialog(MainActivity.this);
            wasteOptionDialog.show();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void isCleanDialog() {
        try {
            View diaLayout = MainActivity.this.getLayoutInflater().inflate(R.layout.dialog_for_is_clean_option, null);
            AlertDialog.Builder alertDialog = new AlertDialog.Builder(MainActivity.this).setView(diaLayout).setCancelable(false);
            isCleanDialog = alertDialog.create();

            diaLayout.findViewById(R.id.clean)
                    .setOnClickListener(view -> {
                        isCleanDialog.dismiss();
                        isClean = true;
                        isPass = true;
                        openCam();

                    });

            diaLayout.findViewById(R.id.not_clean)
                    .setOnClickListener(view -> {
                        isCleanDialog.dismiss();
                        isClean = false;
                        isPass = true;
                        openCam();
                    });

            diaLayout.findViewById(R.id.close_dialog)
                    .setOnClickListener(v -> {
                        isPass = true;
                        isCleanDialog.dismiss();
                    });

            isCleanDialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            isCleanDialog.show();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void changeLangDialog() {
        try {
            cmn.closeAlertDialog(cngLangDialog);
            View diaLayout = MainActivity.this.getLayoutInflater().inflate(R.layout.chnage_lang_dialog_layout, null);
            AlertDialog.Builder alertDialog = new AlertDialog.Builder(MainActivity.this).setView(diaLayout).setCancelable(false);
            cngLangDialog = alertDialog.create();

            if (lang.equals("hi")) {
                diaLayout.findViewById(R.id.hindi_lang).setBackground(getDrawable(R.drawable.selection_background));
            } else {
                diaLayout.findViewById(R.id.english_lang).setBackground(getDrawable(R.drawable.selection_background));
            }

            diaLayout.findViewById(R.id.hindi_lang)
                    .setOnClickListener(view -> {
                        if (isPass) {
                            isPass = false;
                            if (!lang.equals("hi")) {
                                cmn.setLocale("hi", MainActivity.this);
                                refreshActivity();
                            } else {
                                Toast.makeText(MainActivity.this, "Already Selected", Toast.LENGTH_SHORT).show();
                                isPass = true;
                            }
                        }

                    });

            diaLayout.findViewById(R.id.english_lang)
                    .setOnClickListener(view -> {
                        if (isPass) {
                            isPass = false;
                            if (!lang.equals("en")) {
                                cmn.setLocale("en", MainActivity.this);
                                refreshActivity();
                            } else {
                                Toast.makeText(MainActivity.this, "Already Selected", Toast.LENGTH_SHORT).show();
                                isPass = true;
                            }
                        }
                    });

            diaLayout.findViewById(R.id.close_dialog)
                    .setOnClickListener(v -> cngLangDialog.dismiss());

            cngLangDialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            cngLangDialog.show();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void refreshActivity() {
        startActivity(new Intent(this, MainActivity.class));
        finish();
    }

    private void openCam() {
        captureDialog();
    }

    @SuppressLint("ClickableViewAccessibility")
    public void captureDialog() {
        cmn.closeAlertDialog(captureDialog);
        View dialogLayout = MainActivity.this.getLayoutInflater().inflate(R.layout.custom_camera_alertbox, null);
        captureDialog = new AlertDialog.Builder(MainActivity.this).setView(dialogLayout).setCancelable(false).create();
        SurfaceView surfaceView = (SurfaceView) dialogLayout.findViewById(R.id.surfaceViews);
        SurfaceHolder surfaceHolder = surfaceView.getHolder();
        surfaceHolder.setType(SurfaceHolder.SURFACE_TYPE_HARDWARE);
        SurfaceHolder.Callback surfaceViewCallBack = new SurfaceHolder.Callback() {
            @Override
            public void surfaceCreated(SurfaceHolder surfaceHolder) {
                try {
                    mCamera = Camera.open();
                    Camera.Parameters parameters = mCamera.getParameters();
                    List<Camera.Size> sizes = parameters.getSupportedPictureSizes();
                    parameters.setPictureSize(sizes.get(0).width, sizes.get(0).height);
                    mCamera.setParameters(parameters);
                    cmn.setCameraDisplayOrientation(MainActivity.this, 0, mCamera);
                    mCamera.setPreviewDisplay(surfaceHolder);
                    mCamera.startPreview();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

            @Override
            public void surfaceChanged(SurfaceHolder surfaceHolder, int i, int i1, int i2) {

            }

            @Override
            public void surfaceDestroyed(SurfaceHolder surfaceHolder) {

            }
        };
        surfaceHolder.addCallback(surfaceViewCallBack);

        try {
            dialogLayout.findViewById(R.id.capture_image_btn)
                    .setOnClickListener(v -> {
                        if (isPass) {
                            isPass = false;
                            setCaptureBtn();
                        }
                    });

            dialogLayout.findViewById(R.id.close_image_btn).setOnClickListener(v -> {
                captureDialog.cancel();
            });
            captureDialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            captureDialog.show();

            pictureCallback = (bytes, camera) -> {
                Matrix matrix = new Matrix();
                matrix.postRotate(90F);
                Bitmap b = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
                photo = Bitmap.createBitmap(b, 0, 0, b.getWidth(), b.getHeight(), matrix, true);

                if (photo != null) {
                    isPass = true;
                    mRefreshLocation();
                } else {
                    Toast.makeText(this, "Please Retry", Toast.LENGTH_SHORT).show();
                }

                camera.stopPreview();
                if (camera != null) {
                    camera.release();
                    mCamera = null;
                }
                captureDialog.cancel();
            };

            surfaceView.setOnTouchListener((view, motionEvent) -> {
                if (motionEvent.getAction() == MotionEvent.ACTION_DOWN) {
                    try {
                        cmn.focusOnTouch(motionEvent, mCamera, surfaceView);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
                return false;
            });
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    private void mRefreshLocation() {
        if (lastKnownLocation != null) {
            try {
                Geocoder geocoder = new Geocoder(MainActivity.this, Locale.getDefault());
                String[] address = geocoder
                        .getFromLocation(lastKnownLocation.getLatitude(), lastKnownLocation.getLongitude(), 5).get(0).getAddressLine(0)
                        .split(", " + geocoder.getFromLocation(lastKnownLocation.getLatitude(), lastKnownLocation.getLongitude(), 5).get(0).getLocality());

                if (address.length > 0) {
                    wardFromAvailableLatLng(lastKnownLocation, address[0]);
                } else {
                    Toast.makeText(MainActivity.this, "Unable to locate address", Toast.LENGTH_SHORT).show();
                }

            } catch (Exception e) {
                cmn.closeDialog(MainActivity.this);
                Toast.makeText(MainActivity.this, "Please Retry", Toast.LENGTH_SHORT).show();
                e.printStackTrace();
            }
        }
    }

    private void wardFromAvailableLatLng(Location finalLocation, String address) {
        Iterator<String> iterator = boundariesData.keys();

        boolean isIterate = false;

        while (iterator.hasNext()) {
            try {
                String key = iterator.next();

                JSONArray tempLatLngArray = new JSONArray(String.valueOf(boundariesData.get(key)));

                ArrayList<LatLng> latLngOfBoundaryArrayList = new ArrayList<>();

                for (int i = 0; i <= tempLatLngArray.length() - 1; i++) {

                    String[] latlngArray = String.valueOf(tempLatLngArray.get(i)).split(",");

                    latLngOfBoundaryArrayList.add(new LatLng(Double.parseDouble(latlngArray[1].trim()), Double.parseDouble(latlngArray[0].trim())));

                    if (i == tempLatLngArray.length() - 1) {

                        if (PolyUtil.containsLocation(new LatLng(finalLocation.getLatitude(), finalLocation.getLongitude()), latLngOfBoundaryArrayList, true)) {
                            isIterate = true;
                            String[] wrdZoneArr = key.split("_");
                            saveData(finalLocation, address, wrdZoneArr[0].trim(), wrdZoneArr[1].trim());
                            break;
                        }
                    }

                }

                if (isIterate) break;

            } catch (JSONException e) {
                e.printStackTrace();
            }
        }

        if (!isIterate) saveData(finalLocation, address, "Not Found", "Not Found");
    }

    private void saveData(Location finalLocation, String address, String ward, String zone) {
        try {
            rootRef.child("WastebinMonitor/ImagesData/" + cmn.getYear() + "/" + cmn.getMonth() + "/" + cmn.getDate() + "/" + data_to_upload.get("category"))
                    .child("lastKey").runTransaction(new Transaction.Handler() {
                @NonNull
                @Override
                public Transaction.Result doTransaction(@NonNull MutableData currentData) {
                    if (currentData.getValue() == null) {
                        currentData.setValue(1);
                    } else {
                        currentData.setValue(String.valueOf((Integer.parseInt(currentData.getValue().toString()) + 1)));
                    }
                    return Transaction.success(currentData);
                }

                @SuppressLint("SimpleDateFormat")
                @Override
                public void onComplete(@Nullable DatabaseError error, boolean committed, @Nullable DataSnapshot currentData) {
                    if (error == null) {
                        try {
                            assert currentData != null;
                            int key = Integer.parseInt(String.valueOf(currentData.getValue()));
                            String cat = String.valueOf(data_to_upload.get("category"));
                            String imgName = cmn.getYear() + "~" + cmn.getMonth() + "~" + cmn.getDate() + "~" + cat + "~" + key;
                            String[] str = address.split(",");

                            data_to_upload.put("locality", str[str.length - 1]);
                            data_to_upload.remove("category");
                            data_to_upload.put("user", userId);
                            data_to_upload.put("ward", ward);
                            data_to_upload.put("zone", zone);
                            data_to_upload.put("latLng", finalLocation.getLatitude() + "," + finalLocation.getLongitude());
                            data_to_upload.put("address", address);
                            data_to_upload.put("imageRef", imgName);
                            data_to_upload.put("isClean", isClean);
                            data_to_upload.put("time", new SimpleDateFormat("HH:mm").format(new Date()));

                            File file = getFile(key + "~" + cmn.getDate() + "~" + cmn.getTime() + "~" + cat);
                            if (!file.exists()) {
                                try {
                                    ByteArrayOutputStream toUpload = new ByteArrayOutputStream();
                                    Bitmap.createScaledBitmap(photo, 512,
                                            (int) (photo.getHeight() * (512.0 / photo.getWidth())), false)
                                            .compress(Bitmap.CompressFormat.JPEG, 100, toUpload);
                                    FileOutputStream fos = new FileOutputStream(file);
                                    BitmapFactory.decodeByteArray(toUpload.toByteArray(), 0, toUpload.toByteArray().length)
                                            .compress(Bitmap.CompressFormat.JPEG, 100, fos);
                                    imagesList.add(new ModelForImages(cmn.getDate(), cmn.getTime(), Integer.parseInt(cat), file));
                                    fos.flush();
                                    fos.close();

                                   new Thread(() -> {
                                       backUpPref.edit().putInt("key", key).apply();
                                       backUpPref.edit().putString("imageName", file.getName()).apply();
                                       backUpPref.edit().putString("imageNameFirebase", imgName).apply();
                                       backUpPref.edit().putString("year", cmn.getYear()).apply();
                                       backUpPref.edit().putString("month", cmn.getMonth()).apply();
                                       backUpPref.edit().putString("date", cmn.getDate()).apply();
                                       backUpPref.edit().putString("cat", cat).apply();
                                       backUpPref.edit().putString("data", data_to_upload.toString()).apply();
                                       backUpPref.edit().putBoolean("DateWiseTotalCount", true).apply();
                                       backUpPref.edit().putBoolean("CategoryTotalCount", true).apply();
                                       backUpPref.edit().putBoolean("DateWiseCatTotalCount", true).apply();
                                       backUpPref.edit().putBoolean("CategoryWiseTotalCount", true).apply();
                                   }).start();

                                    stoRef.child(imgName)
                                            .putBytes(toUpload.toByteArray())
                                            .addOnCompleteListener(task -> {
                                                if (task.isSuccessful()) {

                                                    backUpPref.edit().remove("imageName").apply();


                                                    new Thread(() -> rootRef.child("WastebinMonitor/ImagesData/" + cmn.getYear() + "/" + cmn.getMonth() + "/" + cmn.getDate() + "/" + cat + "/" + key)
                                                            .setValue(new Gson().fromJson(data_to_upload.toString(), new TypeToken<HashMap<String, Object>>() {
                                                            }.getType()))
                                                            .addOnCompleteListener(task1 -> {
                                                                backUpPref.edit().remove("data").apply();
                                                            })).start();


                                                    new Thread(() -> rootRef.child("WastebinMonitor/UserImageRef/" + userId).push().setValue(imgName)
                                                            .addOnCompleteListener(task1 -> {
                                                                backUpPref.edit().remove("imageNameFirebase").apply();
                                                            })).start();


                                                    new Thread(() -> rootRef.child("WastebinMonitor/Summary/DateWise/" + cmn.getDate() + "/totalCount")
                                                            .runTransaction(new Transaction.Handler() {
                                                                @NonNull
                                                                @Override
                                                                public Transaction.Result doTransaction(@NonNull MutableData currentData1) {
                                                                    if (currentData1.getValue() == null) {
                                                                        currentData1.setValue(1);
                                                                    } else {
                                                                        currentData1.setValue(String.valueOf((Integer.parseInt(currentData1.getValue().toString()) + 1)));
                                                                    }
                                                                    return Transaction.success(currentData1);
                                                                }

                                                                @Override
                                                                public void onComplete(@Nullable DatabaseError error1, boolean committed1, @Nullable DataSnapshot currentData1) {
                                                                    if (error1 == null) {
                                                                        backUpPref.edit().putBoolean("DateWiseTotalCount", false).apply();
                                                                    }
                                                                }
                                                            })).start();


                                                    new Thread(() -> rootRef.child("WastebinMonitor/Summary/CategoryWise/totalCount")
                                                            .runTransaction(new Transaction.Handler() {
                                                                @NonNull
                                                                @Override
                                                                public Transaction.Result doTransaction(@NonNull MutableData currentData12) {
                                                                    if (currentData12.getValue() == null) {
                                                                        currentData12.setValue(1);
                                                                    } else {
                                                                        currentData12.setValue(String.valueOf((Integer.parseInt(currentData12.getValue().toString()) + 1)));
                                                                    }
                                                                    return Transaction.success(currentData12);
                                                                }

                                                                @Override
                                                                public void onComplete(@Nullable DatabaseError error12, boolean committed12, @Nullable DataSnapshot currentData12) {
                                                                    if (error12 == null) {
                                                                        backUpPref.edit().putBoolean("CategoryTotalCount", false).apply();
                                                                    }
                                                                }
                                                            })).start();


                                                    new Thread(() -> rootRef.child("WastebinMonitor/Summary/DateWise/" + cmn.getDate() + "/" + cat + "/totalCount")
                                                            .runTransaction(new Transaction.Handler() {
                                                                @NonNull
                                                                @Override
                                                                public Transaction.Result doTransaction(@NonNull MutableData currentData13) {
                                                                    if (currentData13.getValue() == null) {
                                                                        currentData13.setValue(1);
                                                                    } else {
                                                                        currentData13.setValue(String.valueOf((Integer.parseInt(currentData13.getValue().toString()) + 1)));
                                                                    }
                                                                    return Transaction.success(currentData13);
                                                                }

                                                                @Override
                                                                public void onComplete(@Nullable DatabaseError error13, boolean committed13, @Nullable DataSnapshot currentData13) {
                                                                    if (error13 == null) {
                                                                        backUpPref.edit().putBoolean("DateWiseCatTotalCount", false).apply();
                                                                    }
                                                                }
                                                            })).start();

                                                    new Thread(() -> rootRef.child("WastebinMonitor/Summary/CategoryWise/" + cat + "/totalCount")
                                                            .runTransaction(new Transaction.Handler() {
                                                                @NonNull
                                                                @Override
                                                                public Transaction.Result doTransaction(@NonNull MutableData currentData14) {
                                                                    if (currentData14.getValue() == null) {
                                                                        currentData14.setValue(1);
                                                                    } else {
                                                                        currentData14.setValue(String.valueOf((Integer.parseInt(currentData14.getValue().toString()) + 1)));
                                                                    }
                                                                    return Transaction.success(currentData14);
                                                                }

                                                                @Override
                                                                public void onComplete(@Nullable DatabaseError error14, boolean committed14, @Nullable DataSnapshot currentData14) {
                                                                    if (error14 == null) {
                                                                        backUpPref.edit().putBoolean("CategoryWiseTotalCount", false).apply();
                                                                    }
                                                                }
                                                            })).start();

                                                    cmn.closeDialog(MainActivity.this);
                                                    adapterForExpandableList.notifyDataSetChanged();
                                                }
                                            });
                                } catch (java.io.IOException e) {
                                    e.printStackTrace();
                                }
                            }
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    } else {
                        cmn.closeDialog(MainActivity.this);
                        Toast.makeText(MainActivity.this, "Please Retry", Toast.LENGTH_SHORT).show();
                    }
                }
            });
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public File getFile(String name) {
        ContextWrapper cw = new ContextWrapper(getApplicationContext());
        return new File(cw.getDir("WastebinImages", Context.MODE_PRIVATE), name);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main_activity_menu, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == R.id.logOut) {
            FirebaseAuth.getInstance().signOut();
            cmn.getDatabaseSp(MainActivity.this).edit().putString("uid", "").apply();
            finish();
        }
        if (item.getItemId() == R.id.change_lang) {
            changeLangDialog();
        }

        return super.onOptionsItemSelected(item);

    }

    @Override
    protected void onActivityResult(final int requestCode, final int resultCode, final Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 0002 && resultCode == RESULT_OK) {
            if (isPass) {
                isPass = false;
                wasteBinOptionsDialog();
            }
        }
    }

    private class AdapterForImagesRecyclerView extends RecyclerView.Adapter<AdapterForImagesRecyclerView.MyViewHolder> {
        private List<ModelForImages> imagesList;

        class MyViewHolder extends RecyclerView.ViewHolder {
            ImageView iv;
            TextView datAndTimeTv;

            MyViewHolder(View view) {
                super(view);
                iv = view.findViewById(R.id.iv_for_rv);
                datAndTimeTv = view.findViewById(R.id.date_and_time_tv);
            }
        }

        public AdapterForImagesRecyclerView(List<ModelForImages> imagesList) {
            this.imagesList = imagesList;
        }

        @NonNull
        @Override
        public MyViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View itemView = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.image_recyler_view_layout, parent, false);
            return new MyViewHolder(itemView);
        }

        @SuppressLint("SetTextI18n")
        @Override
        public void onBindViewHolder(MyViewHolder holder, int position) {
            ModelForImages data = imagesList.get(position);
            holder.iv.setImageDrawable(Drawable.createFromPath(data.getImageBitmap().toString()));

            holder.datAndTimeTv.setText(data.getDate() + " " + data.getTime());

            holder.iv.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    dialogForViewingImage(data);
                }
            });
        }

        @Override
        public int getItemCount() {
            return imagesList.size();
        }

        private void dialogForViewingImage(ModelForImages data) {
            try {
                cmn.closeAlertDialog(wasteOptionDialog);
                View diaLayout = MainActivity.this.getLayoutInflater().inflate(R.layout.dialog_for_viewing_image, null);
                AlertDialog.Builder alertDialog = new AlertDialog.Builder(MainActivity.this).setView(diaLayout).setCancelable(true);

                wasteOptionDialog = alertDialog.create();

                ImageView imageView = diaLayout.findViewById(R.id.image_viewing_iv);

                imageView.setImageDrawable(Drawable.createFromPath(data.getImageBitmap().toString()));

                diaLayout.findViewById(R.id.image_viewing_close_btn)
                        .setOnClickListener(view -> wasteOptionDialog.dismiss());


                wasteOptionDialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
                wasteOptionDialog.show();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private class AdapterForImageOptionsList extends BaseAdapter {

        @Override
        public int getCount() {
            return wasteBinTypesList.size();
        }

        @Override
        public Object getItem(int i) {
            return i;
        }

        @Override
        public long getItemId(int i) {
            return i;
        }

        @SuppressLint("ViewHolder")
        @Override
        public View getView(int i, View view, ViewGroup viewGroup) {
            LayoutInflater layoutInflater = (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            view = layoutInflater.inflate(R.layout.image_options_lv_layout, null, true);
            TextView imageOptionTv = view.findViewById(R.id.image_option_tv);
            LinearLayout mainLL = view.findViewById(R.id.main_ll);
            ImageView iconIv = view.findViewById(R.id.icon_image_view);
            ModelWastebinTypes model = wasteBinTypesList.get(i);
            imageOptionTv.setText(model.getType());

            mainLL.setOnClickListener(view1 -> {
                try {
                    data_to_upload.put("category", wasteBinTypesMap.get(model.getType()));
                    if (i == 0) {
                        isTransferStation = true;
                    } else if (i == 1) {
                        isOpenDepo = true;
                    }
                    wasteOptionDialog.dismiss();
                    if (isCleanDialog != null) {
                        isCleanDialog.dismiss();
                    }
                    isCleanDialog();
                } catch (Exception e) {
                    e.printStackTrace();
                }

            });

            switch (i) {
                case 0:
                    iconIv.setImageResource(R.drawable.transfer_station);
                    break;
                case 1:
                    iconIv.setImageResource(R.drawable.not_clean_icon);
                    break;
                case 2:
                    iconIv.setImageResource(R.drawable.litter_dustbin);
                    break;
            }
            return view;
        }
    }

    public class AdapterForExpandableList extends BaseAdapter {
        ArrayList<ModelForImages> tempModel = new ArrayList<>();
        int lastIndex = 0;

        @Override
        public int getCount() {
            return wasteBinTypesList.size();
        }

        @Override
        public Object getItem(int i) {
            return i;
        }

        @Override
        public long getItemId(int i) {
            return i;
        }

        @SuppressLint("ViewHolder")
        @Override
        public View getView(int i, View view, ViewGroup viewGroup) {
            LayoutInflater layoutInflater = (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            view = layoutInflater.inflate(R.layout.expandable_list_view_layout, null, true);
            ModelWastebinTypes modelMAin = wasteBinTypesList.get(i);

            TextView imageOptionTv = view.findViewById(R.id.image_option_tv);
            imageOptionTv.setText(modelMAin.getType());
            RecyclerView recyclerView = view.findViewById(R.id.recyclerView_main_screen);
            TextView dataAlertTv = view.findViewById(R.id.data_alert_tv);
            LinearLayoutManager mLayoutManager = new LinearLayoutManager(getApplicationContext());
            mLayoutManager.setOrientation(LinearLayoutManager.HORIZONTAL);
            recyclerView.setLayoutManager(mLayoutManager);
            recyclerView.setItemAnimator(new DefaultItemAnimator());
            tempModel = new ArrayList<>();

            for (int j = 0; j < imagesList.size(); j++) {
                ModelForImages model = imagesList.get(j);
                if (model.getCategory() == i + 1) {
                    tempModel.add(model);
                }
            }

            AdapterForImagesRecyclerView imageAdapter = new AdapterForImagesRecyclerView(tempModel);
            recyclerView.setAdapter(imageAdapter);


            if (modelMAin.isVisibility()) {
                recyclerView.setVisibility(View.VISIBLE);
                if (tempModel.size() > 0) {
                    dataAlertTv.setVisibility(View.GONE);
                } else {
                    dataAlertTv.setVisibility(View.VISIBLE);
                }
            } else {
                recyclerView.setVisibility(View.GONE);
                dataAlertTv.setVisibility(View.GONE);
            }

            imageOptionTv.setOnClickListener(view1 -> {
                if (lastIndex != i) {
                    wasteBinTypesList.get(lastIndex).setVisibility(false);
                    lastIndex = i;
                    modelMAin.setVisibility(true);
                    notifyDataSetChanged();
                }
            });

            return view;
        }


    }

    public void fetchWardBoundariesData() {
        cmn.setProgressDialog("", "Please Wait", MainActivity.this, MainActivity.this);
        cmn.getStoRef(MainActivity.this).child("/Defaults/BoundariesLatLng.json").getMetadata().addOnSuccessListener(storageMetadata -> {
            long fileCreationTime = storageMetadata.getCreationTimeMillis();
            long fileDownloadTime = cmn.wardBoundPref(MainActivity.this).getLong("BoundariesLatLngDownloadTime", 0);
            if (fileDownloadTime != fileCreationTime) {
                try {
                    File localFile = File.createTempFile("images", "jpg");
                    cmn.getStoRef(MainActivity.this).child("/Defaults/BoundariesLatLng.json").getFile(localFile)
                            .addOnCompleteListener(task -> {
                                try {
                                    BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(localFile)));
                                    StringBuilder sb = new StringBuilder();
                                    String str;
                                    while ((str = br.readLine()) != null) {
                                        sb.append(str);
                                    }
                                    boundariesData = new JSONObject(sb.toString());
                                    cmn.wardBoundPref(MainActivity.this).edit().putString("BoundariesLatLng", sb.toString()).apply();
                                    cmn.wardBoundPref(MainActivity.this).edit().putLong("BoundariesLatLngDownloadTime", fileCreationTime).apply();
                                    cmn.closeDialog(MainActivity.this);
                                } catch (Exception e) {
                                    e.printStackTrace();
                                }
                            });

                } catch (Exception e) {
                    e.printStackTrace();
                }
            } else {
                try {
                    boundariesData = new JSONObject(cmn.wardBoundPref(MainActivity.this).getString("BoundariesLatLng", null));
                    cmn.closeDialog(MainActivity.this);
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
        });
    }

    public void fetchWastebinTypes(Context context) {
        try {
            cmn.getStoRef(context).child("/Defaults/ImageOptionTypes.json").getMetadata().addOnSuccessListener(storageMetadata -> {
                long fileCreationTime = storageMetadata.getCreationTimeMillis();
                long fileDownloadTime = cmn.getDatabaseSp(context).getLong("ImageOptionTypesDownloadTime", 0);
                if (fileDownloadTime != fileCreationTime) {
                    cmn.getStoRef(context).child("/Defaults/ImageOptionTypes.json")
                            .getBytes(10000000)
                            .addOnSuccessListener(taskSnapshot -> {
                                try {
                                    String str = new String(taskSnapshot, StandardCharsets.UTF_8);
                                    JSONArray obj = new JSONArray(str);
                                    ArrayList<String> al_en = new ArrayList<>();
                                    ArrayList<String> al_hi = new ArrayList<>();
                                    for (int i = 0; i < obj.length(); i++) {
                                        if (!obj.get(i).toString().equals("null")) {
                                            JSONObject o = new JSONObject(obj.get(i).toString());
                                            al_hi.add(i + "~" + o.get("hi").toString());
                                            al_en.add(i + "~" + o.get("en").toString());
                                        }
                                    }
                                    Log.d("TAG", "fetchWastebinTypes: " + al_en.toString() + " " + al_hi.toString());
                                    cmn.getDatabaseSp(context).edit().putString("wastebin_types_en", al_en.toString()).apply();
                                    cmn.getDatabaseSp(context).edit().putString("wastebin_types_hi", al_hi.toString()).apply();
                                    cmn.getDatabaseSp(context).edit().putLong("ImageOptionTypesDownloadTime", fileCreationTime).apply();
                                    prepareDS();
                                } catch (Exception e) {
                                    e.printStackTrace();
                                }
                            });
                } else {
                    prepareDS();
                }
            });
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


}