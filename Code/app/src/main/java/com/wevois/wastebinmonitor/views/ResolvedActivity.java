package com.wevois.wastebinmonitor.views;

import androidx.appcompat.app.AppCompatActivity;
import androidx.databinding.DataBindingUtil;
import androidx.lifecycle.ViewModelProviders;

import android.os.Bundle;

import com.wevois.wastebinmonitor.R;
import com.wevois.wastebinmonitor.databinding.ActivityResolvedBinding;
import com.wevois.wastebinmonitor.viewmodel.ResolvedViewModel;

public class ResolvedActivity extends AppCompatActivity {
    ActivityResolvedBinding binding;
    ResolvedViewModel viewModel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = DataBindingUtil.setContentView(this, R.layout.activity_resolved);
        viewModel = ViewModelProviders.of(this).get(ResolvedViewModel.class);
        binding.setResolvedviewmodel(viewModel);
        binding.setLifecycleOwner(this);
        viewModel.init(this);
    }
}