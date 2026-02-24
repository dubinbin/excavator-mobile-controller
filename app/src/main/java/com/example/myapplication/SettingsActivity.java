package com.example.myapplication;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

/**
 * 设置页面 - 目前支持修改视频流地址，后续可扩展更多设置项
 */
public class SettingsActivity extends AppCompatActivity {

    private EditText etSettingsVideoUrl;
    private Button btnSaveVideoUrl;
    private TextView btnBack;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // 全屏模式
        setFullScreenMode();

        setContentView(R.layout.activity_settings);

        etSettingsVideoUrl = findViewById(R.id.etSettingsVideoUrl);
        btnSaveVideoUrl = findViewById(R.id.btnSaveVideoUrl);
        btnBack = findViewById(R.id.btnBack);

        // 获取当前视频地址并显示
        String currentUrl = getIntent().getStringExtra("current_url");
        if (currentUrl != null && !currentUrl.isEmpty()) {
            etSettingsVideoUrl.setText(currentUrl);
        }

        // 返回按钮
        btnBack.setOnClickListener(v -> finish());

        // 保存按钮
        btnSaveVideoUrl.setOnClickListener(v -> {
            String newUrl = etSettingsVideoUrl.getText().toString().trim();
            if (!newUrl.isEmpty()) {
                Intent resultIntent = new Intent();
                resultIntent.putExtra("video_url", newUrl);
                setResult(RESULT_OK, resultIntent);
                Toast.makeText(this, "视频地址已保存", Toast.LENGTH_SHORT).show();
                finish();
            } else {
                Toast.makeText(this, "请输入有效的 RTSP 地址", Toast.LENGTH_SHORT).show();
            }
        });
    }

    /**
     * 设置全屏模式（隐藏状态栏和导航栏）
     */
    private void setFullScreenMode() {
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        WindowInsetsControllerCompat controller = WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        if (controller != null) {
            controller.hide(WindowInsetsCompat.Type.systemBars());
            controller.setSystemBarsBehavior(WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            setFullScreenMode();
        }
    }
}
