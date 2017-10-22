package com.zy.xxl.zyfiledownloader;

import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.View;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
    }

    public void onTaskManagerClick(View view) {
        startActivity(new Intent(this, TasksManagerDemoActivity.class));
    }

    public void onSingleTask(View view) {
        startActivity(new Intent(this, SingleTaskTestActivity.class));
    }
}
