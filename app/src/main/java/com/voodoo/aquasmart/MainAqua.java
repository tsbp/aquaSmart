package com.voodoo.aquasmart;

import android.app.Activity;
import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.SimpleAdapter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class MainAqua extends Activity {

    String[] names = { "Компрессор", "Фильтр"};
    String[] start = { "12:00", "14:24"};
    String[] stop = { "16:00", "22:24"};

    private final String ATTRIBUTE_TITLE = "title";
    private final String ATTRIBUTE_TIME_START = "tStart";
    private final String ATTRIBUTE_TIME_STOP = "tStop";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main_aqua);

        // находим список
        ListView lvMain = (ListView) findViewById(R.id.lvPeripherial);

        // создаем адаптер
        ArrayList<Map<String, Object>> data = new ArrayList<>(
                names.length);
        Map<String, Object> m;
        for (int i = 0; i < names.length; i++) {
            m = new HashMap<>();
            m.put(ATTRIBUTE_TITLE,  names[i]);

            m.put(ATTRIBUTE_TIME_START, start[i]);
            m.put(ATTRIBUTE_TIME_STOP, stop[i]);
            data.add(m);
        }
        String[] from = {ATTRIBUTE_TITLE, ATTRIBUTE_TIME_START, ATTRIBUTE_TIME_STOP};
        int[] to = {R.id.tvTitle, R.id.tvStart, R.id.tvStop};
        SimpleAdapter sAdapter = new SimpleAdapter(this, data, R.layout.peripherial, from, to);
        lvMain.setAdapter(sAdapter);
    }
}
