package com.wevois.wastebinmonitor.viewmodel;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.databinding.BindingAdapter;
import androidx.databinding.ObservableField;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.wevois.wastebinmonitor.CommonFunctions;
import com.wevois.wastebinmonitor.R;
import com.wevois.wastebinmonitor.views.ResolvedActivity;

public class ResolvedViewModel extends ViewModel {
    Activity activity;
    CommonFunctions common = new CommonFunctions();
    StorageReference stoRef;
    String year="",month="",date="",category="",sNo="";
    public MutableLiveData<Bitmap> imageView1Url = new MutableLiveData<>();
    public ObservableField<Boolean> isVisible = new ObservableField<>(false);
    public ObservableField<Boolean> isVisible1 = new ObservableField<>(true);
    public ObservableField<String> type = new ObservableField<>("Complaint");
    String imagePath="";

    public void init(ResolvedActivity resolvedActivity) {
        activity = resolvedActivity;
        imagePath = activity.getIntent().getExtras().getString("reference");
        activity.runOnUiThread(() -> downloadAndShowImage(imagePath));
    }

    @BindingAdapter({"imageUrl"})
    public static void loadImage(ImageView view, Bitmap bitmap) {
        if(bitmap==null){
            view.setImageResource(R.drawable.img_not_available);
        }else {
            view.setImageBitmap(bitmap);
        }
        view.setScaleType(ImageView.ScaleType.FIT_XY);
    }

    private void downloadAndShowImage(String imageName) {
        try {
            common.setProgressDialog("", "Loading Image", activity,activity);

            String[] stringData = imageName.split("~");
            year = stringData[0];
            month = stringData[1];
            date = stringData[2];
            category = stringData[3];
            sNo = stringData[4];

            stoRef = common.getStoRef(activity).child("WastebinMonitorImages/" + year + "/" + month + "/" + date);
            Log.d("TAG", "downloadAndShowImage: check A" + stoRef.child(imageName).toString());
            FirebaseStorage.getInstance()
                    .getReferenceFromUrl(stoRef.child(imageName).toString())
                    .getBytes(1024 * 1024)
                    .addOnSuccessListener(bytes -> {
                        Log.d("TAG", "downloadAndShowImage: check " + bytes);
                        imageView1Url.setValue(BitmapFactory.decodeByteArray(bytes, 0, bytes.length));
                        common.closeDialog(activity);
                    }).addOnFailureListener(e -> {
                Log.d("TAG", "downloadAndShowImage: check " + e.toString());
                common.closeDialog(activity);
            });
        } catch (Exception exc) {
            exc.printStackTrace();
        }
    }

    public void dirtyBtn() {
        isVisible.set(false);
        isVisible1.set(true);
        type.set("Complaint");
        downloadAndShowImage(imagePath);
    }

    public void cleanBtn() {
        isVisible.set(true);
        isVisible1.set(false);
        type.set("Resolved");
        downloadAndShowImage(imagePath+"~RESOLVED");
    }
}
