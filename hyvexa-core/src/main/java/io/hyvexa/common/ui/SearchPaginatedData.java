package io.hyvexa.common.ui;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;

public class SearchPaginatedData extends ButtonEventData {

    public static final String KEY_SEARCH = "@Search";

    public static final BuilderCodec<SearchPaginatedData> CODEC =
            BuilderCodec.<SearchPaginatedData>builder(SearchPaginatedData.class, SearchPaginatedData::new)
                    .addField(new KeyedCodec<>(ButtonEventData.KEY_BUTTON, Codec.STRING),
                            (data, value) -> data.button = value, data -> data.button)
                    .addField(new KeyedCodec<>(KEY_SEARCH, Codec.STRING),
                            (data, value) -> data.search = value, data -> data.search)
                    .build();

    private String button;
    private String search;

    @Override
    public String getButton() {
        return button;
    }

    public String getSearch() {
        return search;
    }
}
