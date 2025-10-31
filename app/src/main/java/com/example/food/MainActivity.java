package com.example.food;

import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.MenuItem;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;

import android.widget.FrameLayout;

import com.google.firebase.auth.FirebaseAuth;
import com.google.android.material.bottomnavigation.BottomNavigationView;

public class MainActivity extends AppCompatActivity {

    private FirebaseAuth mAuth;
    private TextView tvWelcome;
    private Button btnLogout;
    private FrameLayout addButtonOverlay;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Configure status bar and navigation bar colors
        setupSystemBars();
        
        setContentView(R.layout.activity_main);

        // Initialize FirebaseAuth instance
        mAuth = FirebaseAuth.getInstance();

        // Check if user is already logged in
        if (mAuth.getCurrentUser() == null) {
            // User not logged in, redirect to login page
            startActivity(new Intent(MainActivity.this, LoginActivity.class));
            finish();
            return;
        }

        BottomNavigationView bottomNav = findViewById(R.id.bottom_nav);
        bottomNav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            
            // Update icons based on selection
            updateNavIcons(bottomNav, id);
            
            if (id == R.id.nav_home) {
                switchFragment(new HomeFragment());
                return true;
            } else if (id == R.id.nav_map) {
                switchFragment(new MapFragment());
                return true;
            } else if (id == R.id.nav_add) {
                switchFragment(new AddFragment());
                return true;
            } else if (id == R.id.nav_profile) {
                switchFragment(new ProfileFragment());
                return true;
            } else if (id == R.id.nav_settings) {
                switchFragment(new SettingsFragment());
                return true;
            }
            return false;
        });

        bottomNav.setSelectedItemId(R.id.nav_home);
        updateNavIcons(bottomNav, R.id.nav_home);
        
        // Auto-upload functionality deleted, now only loads data from Firebase
    }
    
    @Override
    protected void onResume() {
        super.onResume();
        Intent intent = getIntent();
        if (intent != null && intent.hasExtra("open_restaurant_id")) {
            String restaurantId = intent.getStringExtra("open_restaurant_id");
            // Select map tab and deliver the restaurantId to MapFragment
            BottomNavigationView bottomNav = findViewById(R.id.bottom_nav);
            bottomNav.setSelectedItemId(R.id.nav_map);
            // Attach restaurant ID as argument
            MapFragment mapFragment = new MapFragment();
            Bundle args = new Bundle();
            args.putString("open_restaurant_id", restaurantId);
            mapFragment.setArguments(args);
            switchFragment(mapFragment);
            // clear so it doesn't repeat
            intent.removeExtra("open_restaurant_id");
        }
    }

    // Upload functionality deleted, now only loads data from Firebase

    private void setupSystemBars() {
        Window window = getWindow();
        
        // Set status bar color to white with black text
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            window.setStatusBarColor(ContextCompat.getColor(this, R.color.white));
        }
        
        // Set navigation bar color to white with black text
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            window.setNavigationBarColor(ContextCompat.getColor(this, R.color.white));
        }
        
        // Enable light status bar (black text/icons)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            window.getDecorView().setSystemUiVisibility(
                window.getDecorView().getSystemUiVisibility() | 
                View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
            );
        }
        
        // Enable light navigation bar (black text/icons)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            window.getDecorView().setSystemUiVisibility(
                window.getDecorView().getSystemUiVisibility() | 
                View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
            );
        }
    }

    private void switchFragment(Fragment fragment) {
        FragmentManager fm = getSupportFragmentManager();
        FragmentTransaction tx = fm.beginTransaction();
        tx.replace(R.id.fragment_container, fragment);
        tx.commit();
    }
    
    private void updateNavIcons(BottomNavigationView bottomNav, int selectedId) {
        // Reset all icons to outline versions
        bottomNav.getMenu().findItem(R.id.nav_home).setIcon(R.drawable.ic_home_outline);
        bottomNav.getMenu().findItem(R.id.nav_map).setIcon(R.drawable.ic_map_outline);
        bottomNav.getMenu().findItem(R.id.nav_add).setIcon(R.drawable.ic_add_outline);
        bottomNav.getMenu().findItem(R.id.nav_profile).setIcon(R.drawable.ic_profile_outline);
        bottomNav.getMenu().findItem(R.id.nav_settings).setIcon(R.drawable.ic_settings_outline);
        
        // Set the selected icon to filled version
        if (selectedId == R.id.nav_home) {
            bottomNav.getMenu().findItem(R.id.nav_home).setIcon(R.drawable.ic_home_filled);
        } else if (selectedId == R.id.nav_map) {
            bottomNav.getMenu().findItem(R.id.nav_map).setIcon(R.drawable.ic_map_filled);
        } else if (selectedId == R.id.nav_add) {
            // Add button becomes filled when selected
            bottomNav.getMenu().findItem(R.id.nav_add).setIcon(R.drawable.ic_add_filled);
        } else if (selectedId == R.id.nav_profile) {
            bottomNav.getMenu().findItem(R.id.nav_profile).setIcon(R.drawable.ic_profile_filled);
        } else if (selectedId == R.id.nav_settings) {
            bottomNav.getMenu().findItem(R.id.nav_settings).setIcon(R.drawable.ic_settings_filled);
        }
    }
}