package com.rmkrings.pius_app_for_android;

import android.support.v4.app.FragmentTransaction;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.design.widget.BottomNavigationView;
import android.support.v7.app.AppCompatActivity;
import android.view.MenuItem;
import android.content.Intent;

import com.rmkrings.main.pius_app.CalendarFragment;
import com.rmkrings.main.pius_app.DashboardFragment;
import com.rmkrings.main.pius_app.VertretungsplanFragment;
import com.rmkrings.main.pius_app.TodayFragment;

public class MainActivity extends AppCompatActivity
{

    private BottomNavigationView.OnNavigationItemSelectedListener mOnNavigationItemSelectedListener
            = new BottomNavigationView.OnNavigationItemSelectedListener() {

        @Override
        public boolean onNavigationItemSelected(@NonNull MenuItem item) {
            switch (item.getItemId()) {
                case R.id.navigation_home: {
                    FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
                    transaction.replace(R.id.frameLayout, new TodayFragment());
                    transaction.addToBackStack(null);
                    transaction.commit();
                    return true;
                }

                case R.id.navigation_substitution_schedule: {
                    FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
                    transaction.replace(R.id.frameLayout, new VertretungsplanFragment());
                    transaction.addToBackStack(null);
                    transaction.commit();
                    return true;
                }

                case R.id.navigation_dashboard: {
                    FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
                    transaction.replace(R.id.frameLayout, new DashboardFragment());
                    transaction.addToBackStack(null);
                    transaction.commit();
                    return true;
                }

                case R.id.navigation_calendar: {
                    FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
                    transaction.replace(R.id.frameLayout, new CalendarFragment());
                    transaction.addToBackStack(null);
                    transaction.commit();
                    return true;
                }

                case R.id.navigation_settings:
                    Intent a = new Intent(MainActivity.this, SettingsActivity.class);
                    startActivity(a);
                    return false;
            }
            return false;
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Add navigation bar.
        BottomNavigationView navigation = findViewById(R.id.navigation);
        navigation.setOnNavigationItemSelectedListener(mOnNavigationItemSelectedListener);

        // Show Today fragment initially.
        FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
        transaction.replace(R.id.frameLayout, new TodayFragment());
        transaction.commit();
    }

    @Override
    public void onResume() {
        super.onResume();
        setTitle(R.string.title_home);
    }
}
