package com.vn.jet.mosco.database;

import androidx.room.TypeConverter;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.lang.reflect.Type;
import java.util.Collections;
import java.util.List;

/**
 * Converter để Room DB có thể lưu trữ List<String> (Danh sách thẻ Showcase)
 * dưới dạng JSON String.
 */
public class ShowcaseConverter {
    @TypeConverter
    public String fromList(List<String> list) {
        if (list == null) return "[]";
        return new Gson().toJson(list);
    }

    @TypeConverter
    public List<String> toList(String json) {
        if (json == null || json.isEmpty()) return Collections.emptyList();
        Type type = new TypeToken<List<String>>() {}.getType();
        return new Gson().fromJson(json, type);
    }
}
