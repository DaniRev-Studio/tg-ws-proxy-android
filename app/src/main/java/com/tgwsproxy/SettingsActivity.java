package com.tgwsproxy;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import java.security.SecureRandom;

public class SettingsActivity extends AppCompatActivity {

    private EditText editHost, editPort, editSecret, editDcIps, editCfDomain;
    private Switch   switchAutostart, switchFallbackCf;
    private TextView txtSaveHint;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        editHost        = findViewById(R.id.editHost);
        editPort        = findViewById(R.id.editPort);
        editSecret      = findViewById(R.id.editSecret);
        editDcIps       = findViewById(R.id.editDcIps);
        editCfDomain    = findViewById(R.id.editCfDomain);
        switchAutostart = findViewById(R.id.switchAutostart);
        switchFallbackCf = findViewById(R.id.switchFallbackCf);
        txtSaveHint     = findViewById(R.id.txtSaveHint);

        Button      btnSave         = findViewById(R.id.btnSave);
        Button      btnRandomSecret = findViewById(R.id.btnRandomSecret);
        ImageButton btnBack         = findViewById(R.id.btnBack);

        // Load saved values
        editHost.setText(ProxyPrefs.getHost(this));
        editPort.setText(String.valueOf(ProxyPrefs.getPort(this)));
        editSecret.setText(ProxyPrefs.getSecret(this));
        editDcIps.setText(ProxyPrefs.getDcIps(this));
        editCfDomain.setText(ProxyPrefs.getCfDomain(this));
        switchAutostart.setChecked(ProxyPrefs.getAutostart(this));
        switchFallbackCf.setChecked(ProxyPrefs.getFallbackCf(this));

        editSecret.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
            public void onTextChanged(CharSequence s, int st, int b, int c) {}
            public void afterTextChanged(Editable s) {
                String v = s.toString().trim();
                if (!v.isEmpty() && (v.length() != 32 || !v.matches("[0-9a-fA-F]+"))) {
                    editSecret.setTextColor(0xFFf44336);
                } else {
                    editSecret.setTextColor(0xFF7eb8f7);
                }
            }
        });

        btnRandomSecret.setOnClickListener(v -> {
            byte[] bytes = new byte[16];
            new SecureRandom().nextBytes(bytes);
            StringBuilder sb = new StringBuilder();
            for (byte b : bytes) sb.append(String.format("%02x", b));
            editSecret.setText(sb.toString());
        });

        btnBack.setOnClickListener(v -> finish());

        btnSave.setOnClickListener(v -> {
            if (validate()) {
                save();
                if (ProxyService.running) {
                    Toast.makeText(this, "Сохранено ✓", Toast.LENGTH_SHORT).show();
                    txtSaveHint.setTextColor(0xFFf9a825);
                    txtSaveHint.setText("⚠ Перезапусти прокси чтобы применить изменения");
                } else {
                    Toast.makeText(this, "Сохранено ✓", Toast.LENGTH_SHORT).show();
                    btnSave.postDelayed(this::finish, 800);
                }
            }
        });
    }

    private boolean validate() {
        String host = editHost.getText().toString().trim();
        if (host.isEmpty()) { editHost.setError("Укажи адрес"); return false; }

        String portStr = editPort.getText().toString().trim();
        int port;
        try { port = Integer.parseInt(portStr); }
        catch (NumberFormatException e) { editPort.setError("Неверный порт"); return false; }
        if (port < 1024 || port > 65535) {
            editPort.setError("Порт от 1024 до 65535"); return false;
        }

        String secret = editSecret.getText().toString().trim();
        if (!secret.isEmpty() && (secret.length() != 32 || !secret.matches("[0-9a-fA-F]+"))) {
            editSecret.setError("32 hex-символа или оставь пустым"); return false;
        }

        for (String line : editDcIps.getText().toString().trim().split("\n")) {
            line = line.trim();
            if (line.isEmpty()) continue;
            if (!line.matches("\\d+:.+")) {
                editDcIps.setError("Формат: номер_DC:IP  (например 2:149.154.167.220)");
                return false;
            }
        }
        return true;
    }

    private void save() {
        ProxyPrefs.saveAll(this,
                editHost.getText().toString().trim(),
                Integer.parseInt(editPort.getText().toString().trim()),
                editSecret.getText().toString().trim(),
                editDcIps.getText().toString().trim(),
                switchAutostart.isChecked(),
                editCfDomain.getText().toString().trim(),
                "",   // cf_worker — не используем пока
                switchFallbackCf.isChecked());
    }
}
