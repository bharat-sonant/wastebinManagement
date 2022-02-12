package com.wevois.wastebinmonitor;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.TextView;

import com.airbnb.lottie.LottieAnimationView;
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GoogleAuthProvider;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.messaging.FirebaseMessaging;


import java.util.HashMap;
import java.util.Objects;

public class LoginActivity extends AppCompatActivity {
    private GoogleSignInClient mGoogleSignInClient;
    private FirebaseAuth mAuth;
    private static final int RC_SIGN_IN = 9001;
    DatabaseReference rootRef;
    LottieAnimationView progressBarAnim;
    CommonFunctions cmn = new CommonFunctions();
    boolean isPass = true;
    SharedPreferences pref;
    String token = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);


        try {
            GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                    .requestIdToken("381118272786-govj6slvjf5uathafc3lm8fq9r79qtiq.apps.googleusercontent.com")
                    .requestEmail()
                    .build();

            mGoogleSignInClient = GoogleSignIn.getClient(this, gso);
            inIt();
        } catch (Exception e) {
            e.printStackTrace();
        }
        FirebaseMessaging.getInstance().getToken().addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                token = task.getResult();
            }
        });
    }

    private void inIt() {
        pref = getSharedPreferences("LoginDetails", MODE_PRIVATE);
        TextView loginMessage = findViewById(R.id.loginMessage);
        loginMessage.setText(pref.getString("loginMessage", ""));
        mAuth = FirebaseAuth.getInstance();
        progressBarAnim = findViewById(R.id.please_wait_anim);
    }

    public void googleSignInBtn(View view) {
        if (isPass) {
            isPass = false;
            Intent signInIntent = mGoogleSignInClient.getSignInIntent();
            startActivityForResult(signInIntent, RC_SIGN_IN);
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        FirebaseUser currentUser = mAuth.getCurrentUser();
        updateUI(currentUser);
    }

    private void firebaseAuthWithGoogle(String idToken) {
        AuthCredential credential = GoogleAuthProvider.getCredential(idToken, null);
        mAuth.signInWithCredential(credential)
                .addOnCompleteListener(this, task -> {
                    if (task.isSuccessful()) {
                        FirebaseUser user = mAuth.getCurrentUser();
                        rootRef = cmn.getDatabaseRef(this);
                        rootRef.child("WastebinMonitor/Users/" + user.getUid()).addListenerForSingleValueEvent(new ValueEventListener() {
                            @Override
                            public void onDataChange(@NonNull DataSnapshot snapshot) {
                                if (snapshot.getValue() == null) {
                                    HashMap<String, Object> hashMap = new HashMap<>();
                                    hashMap.put("email", user.getEmail());
                                    hashMap.put("name", user.getDisplayName());
                                    hashMap.put("date", cmn.getDate());
                                    hashMap.put("token", token);
                                    rootRef.child("WastebinMonitor/Users/" + user.getUid() + "/").setValue(hashMap);
                                }else {
                                    HashMap<String, Object> hashMap = new HashMap<>();
                                    hashMap.put("token", token);
                                    rootRef.child("WastebinMonitor/Users/" + user.getUid() + "/").updateChildren(hashMap);
                                }
                                updateUI(user);
                            }

                            @Override
                            public void onCancelled(@NonNull DatabaseError error) {

                            }
                        });

                    } else {
                        updateUI(null);
                    }
                });
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == RC_SIGN_IN && resultCode == RESULT_OK) {
            Task<GoogleSignInAccount> task = GoogleSignIn.getSignedInAccountFromIntent(data);
            isPass = true;
            try {
                progressBarAnim.setVisibility(View.VISIBLE);
                GoogleSignInAccount account = task.getResult(ApiException.class);
                firebaseAuthWithGoogle(account.getIdToken());
            } catch (ApiException e) {
                e.printStackTrace();
            }
        } else {
            isPass = true;
        }
    }

    private void updateUI(FirebaseUser user) {
        if (user != null) {
            pref.edit().putString("uid", user.getUid()).apply();
            pref.edit().putString("name", Objects.requireNonNull(user).getDisplayName()).apply();
            pref.edit().putString("email", user.getEmail()).apply();
            startActivity(new Intent(LoginActivity.this, MainActivity.class));
            finish();
        }

    }

}