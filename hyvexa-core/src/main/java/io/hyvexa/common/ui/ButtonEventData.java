package io.hyvexa.common.ui;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;

public class ButtonEventData {

    public static final String KEY_BUTTON = "Button";
    public static final BuilderCodec<ButtonEventData> CODEC =
            BuilderCodec.<ButtonEventData>builder(ButtonEventData.class, ButtonEventData::new)
                    .addField(new KeyedCodec<>(KEY_BUTTON, Codec.STRING),
                            (data, value) -> data.button = value, data -> data.button)
                    .build();

    // Mutated by BuilderCodec during deserialization — intentional, not a bug.
    private String button;

    public String getButton() {
        return button;
    }
}
