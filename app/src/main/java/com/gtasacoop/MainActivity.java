package com.gtasacoop;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.graphics.Typeface;
import android.view.View;
import android.view.Gravity;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONObject;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newCachedThreadPool();
    private DatagramSocket socket;
    private volatile boolean running = false;
    private EditText ipInput, xInput, yInput, zInput;
    private TextView status, received, log;
    private Button hostButton, connectButton, sendButton;
    private int port = 24680;
    private volatile String mode = "Отключено";
    private volatile InetAddress peerAddress;
    private volatile int peerPort = -1;

    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        buildUi();
    }

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(18), dp(18), dp(18));
        root.setBackgroundColor(0xFFF4F6F8);
        scroll.addView(root);

        TextView title = new TextView(this);
        title.setText("GTASA CO-OP");
        title.setTextSize(27);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        title.setTextColor(0xFF101820);
        root.addView(title);

        TextView subtitle = new TextView(this);
        subtitle.setText("v2.0 • Wi‑Fi прототип передачи координат");
        subtitle.setTextSize(14);
        subtitle.setTextColor(0xFF46515C);
        root.addView(subtitle, margin(0, 0, 0, 14));

        status = new TextView(this);
        status.setText("Статус: Отключено • UDP порт " + port);
        status.setTextSize(16);
        status.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        root.addView(status, margin(0, 8, 0, 12));

        hostButton = button("Создать мир (Хост)");
        connectButton = button("Подключиться к хосту");
        root.addView(hostButton, margin(0, 0, 0, 8));
        root.addView(connectButton, margin(0, 0, 0, 12));

        ipInput = new EditText(this);
        ipInput.setSingleLine(true);
        ipInput.setHint("IP телефона-хоста, например 192.168.1.10");
        ipInput.setTextSize(15);
        root.addView(ipInput, margin(0, 0, 0, 12));

        TextView coordsTitle = new TextView(this);
        coordsTitle.setText("Тестовые координаты игрока");
        coordsTitle.setTextSize(18);
        coordsTitle.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        root.addView(coordsTitle, margin(0, 4, 0, 6));

        LinearLayout coords = new LinearLayout(this);
        coords.setOrientation(LinearLayout.HORIZONTAL);
        xInput = coordInput("X", "0.0");
        yInput = coordInput("Y", "0.0");
        zInput = coordInput("Z", "3.0");
        coords.addView(xInput, new LinearLayout.LayoutParams(0, dp(52), 1));
        coords.addView(yInput, new LinearLayout.LayoutParams(0, dp(52), 1));
        coords.addView(zInput, new LinearLayout.LayoutParams(0, dp(52), 1));
        root.addView(coords, margin(0, 0, 0, 10));

        sendButton = button("Отправить координаты");
        root.addView(sendButton, margin(0, 0, 0, 14));

        TextView receiveTitle = new TextView(this);
        receiveTitle.setText("Последние данные от второго телефона");
        receiveTitle.setTextSize(17);
        receiveTitle.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        root.addView(receiveTitle, margin(0, 0, 0, 5));

        received = new TextView(this);
        received.setText("Пока данных нет");
        received.setTextSize(15);
        received.setTextColor(0xFF17324D);
        received.setPadding(dp(12), dp(12), dp(12), dp(12));
        received.setBackgroundColor(0xFFE2EAF1);
        root.addView(received, margin(0, 0, 0, 14));

        TextView logTitle = new TextView(this);
        logTitle.setText("Журнал");
        logTitle.setTextSize(17);
        logTitle.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        root.addView(logTitle);

        log = new TextView(this);
        log.setText("Приложение готово. Оба телефона должны быть в одной локальной сети.");
        log.setTextSize(13);
        log.setTextColor(0xFF374151);
        log.setPadding(dp(10), dp(10), dp(10), dp(10));
        log.setBackgroundColor(0xFFFFFFFF);
        root.addView(log, margin(0, 5, 0, 12));

        TextView warning = new TextView(this);
        warning.setText("Важно: это тест связи. Координаты пока вводятся вручную — игра GTA SA ещё не подключена к приложению.");
        warning.setTextSize(13);
        warning.setTextColor(0xFF8A3B12);
        root.addView(warning);

        setContentView(scroll);

        hostButton.setOnClickListener(v -> startHost());
        connectButton.setOnClickListener(v -> connectToHost());
        sendButton.setOnClickListener(v -> sendCoordinates());
    }

    private EditText coordInput(String hint, String value) {
        EditText e = new EditText(this);
        e.setSingleLine(true);
        e.setHint(hint);
        e.setText(value);
        e.setInputType(8194);
        e.setTextSize(16);
        return e;
    }

    private Button button(String text) {
        Button b = new Button(this);
        b.setText(text);
        b.setAllCaps(false);
        return b;
    }

    private LinearLayout.LayoutParams margin(int l, int t, int r, int b) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
        p.setMargins(dp(l), dp(t), dp(r), dp(b));
        return p;
    }

    private int dp(int n) {
        return (int)(n * getResources().getDisplayMetrics().density + 0.5f);
    }

    private void startHost() {
        stopSocket();
        worker.execute(() -> {
            try {
                socket = new DatagramSocket(port);
                running = true;
                mode = "Хост";
                ui.post(() -> {
                    status.setText("Статус: Хост работает • UDP " + port);
                    addLog("Создан мир. Сообщения ожидаются на порту " + port + ".");
                    Toast.makeText(this, "Хост запущен", Toast.LENGTH_SHORT).show();
                });
                receiveLoop();
            } catch (Exception e) {
                ui.post(() -> addLog("Не удалось запустить хост: " + e.getMessage()));
            }
        });
    }

    private void connectToHost() {
        String ip = ipInput.getText().toString().trim();
        if (ip.isEmpty()) {
            ipInput.setError("Введи IP телефона-хоста");
            return;
        }
        stopSocket();
        worker.execute(() -> {
            try {
                socket = new DatagramSocket();
                socket.setSoTimeout(0);
                running = true;
                mode = "Клиент";
                ui.post(() -> {
                    status.setText("Статус: Клиент • хост " + ip + ":" + port);
                    addLog("Подключение настроено к " + ip + ":" + port + ". Отправь тестовое сообщение.");
                });
                receiveLoop();
            } catch (Exception e) {
                ui.post(() -> addLog("Не удалось подключиться: " + e.getMessage()));
            }
        });
    }

    private void receiveLoop() {
        byte[] buffer = new byte[2048];
        while (running && socket != null && !socket.isClosed()) {
            try {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                socket.receive(packet);
                String message = new String(packet.getData(), packet.getOffset(),
                        packet.getLength(), StandardCharsets.UTF_8);
                String peer = packet.getAddress().getHostAddress();
                if ("Хост".equals(mode)) {
                    peerAddress = packet.getAddress();
                    peerPort = packet.getPort();
                }
                ui.post(() -> {
                    received.setText("От " + peer + ":\n" + message);
                    addLog("Получено от " + peer);
                });
            } catch (Exception e) {
                if (running) ui.post(() -> addLog("Ошибка приёма: " + e.getMessage()));
                break;
            }
        }
    }

    private void sendCoordinates() {
        if (!running || socket == null || socket.isClosed()) {
            Toast.makeText(this, "Сначала создай мир или подключись", Toast.LENGTH_SHORT).show();
            return;
        }
        final String ip = ipInput.getText().toString().trim();
        if ("Клиент".equals(mode) && ip.isEmpty()) {
            ipInput.setError("Введи IP хоста");
            return;
        }
        try {
            double x = Double.parseDouble(xInput.getText().toString().trim());
            double y = Double.parseDouble(yInput.getText().toString().trim());
            double z = Double.parseDouble(zInput.getText().toString().trim());
            JSONObject obj = new JSONObject();
            obj.put("type", "player_state");
            obj.put("player", android.os.Build.MODEL);
            obj.put("x", x);
            obj.put("y", y);
            obj.put("z", z);
            obj.put("action", "ON_FOOT");
            obj.put("timestamp", System.currentTimeMillis());
            byte[] data = obj.toString().getBytes(StandardCharsets.UTF_8);

            worker.execute(() -> {
                try {
                    InetAddress address;
                    int destinationPort;
                    if ("Клиент".equals(mode)) {
                        address = InetAddress.getByName(ip);
                        destinationPort = port;
                    } else {
                        address = peerAddress;
                        destinationPort = peerPort;
                        if (address == null || destinationPort < 1) {
                            ui.post(() -> addLog("Сначала клиент должен отправить данные хосту."));
                            return;
                        }
                    }
                    DatagramPacket packet = new DatagramPacket(data, data.length, address, destinationPort);
                    socket.send(packet);
                    ui.post(() -> addLog(String.format(Locale.US, "Отправлено: X=%.2f Y=%.2f Z=%.2f", x, y, z)));
                } catch (Exception e) {
                    ui.post(() -> addLog("Ошибка отправки: " + e.getMessage()));
                }
            });
        } catch (Exception e) {
            Toast.makeText(this, "Проверь координаты X, Y, Z", Toast.LENGTH_SHORT).show();
        }
    }

    private void addLog(String line) {
        if (log == null) return;
        String old = log.getText().toString();
        String next = line + "\n" + old;
        if (next.length() > 1400) next = next.substring(0, 1400);
        log.setText(next);
    }

    private void stopSocket() {
        running = false;
        if (socket != null) {
            socket.close();
            socket = null;
        }
    }

    @Override protected void onDestroy() {
        stopSocket();
        worker.shutdownNow();
        super.onDestroy();
    }
}
