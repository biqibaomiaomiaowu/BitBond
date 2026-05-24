package com.bitbond.app;

import android.app.Activity;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.TextView;

public class MainActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        TextView placeholder = new TextView(this);
        placeholder.setText(getString(R.string.app_name));
        placeholder.setGravity(Gravity.CENTER);
        placeholder.setTextSize(24);
        setContentView(placeholder);
    }
}
