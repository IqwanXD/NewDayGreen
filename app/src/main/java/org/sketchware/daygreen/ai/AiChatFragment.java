package org.sketchware.daygreen.ai;

import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.database.Cursor;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.OpenableColumns;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.PathParser;
import androidx.fragment.app.Fragment;

import com.google.android.material.chip.Chip;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.switchmaterial.SwitchMaterial;

import org.sketchware.daygreen.ai.activities.AiSettingsActivity;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import pro.sketchware.databinding.FragmentAiChatBinding;
import pro.sketchware.utility.SketchwareUtil;

public class AiChatFragment extends Fragment {

    private static final String ARG_SC_ID = "sc_id";
    private static final String ARG_JAVA_NAME = "java_name";

    private static final String UI_PREFS = "ai_chat_ui";
    private static final String KEY_DEEP_THINKING = "deep_thinking";
    private static final int MAX_FILE_BYTES = 40_000;

    // Path ikon Material (viewport 24x24)
    private static final String ICON_LINK = "M3.9,12c0,-1.71 1.39,-3.1 3.1,-3.1h4V7H7c-2.76,0 -5,2.24 -5,5s2.24,5 5,5h4v-1.9H7c-1.71,0 -3.1,-1.39 -3.1,-3.1zM8,13h8v-2H8v2zM17,7h-4v1.9h4c1.71,0 3.1,1.39 3.1,3.1s-1.39,3.1 -3.1,3.1h-4V17h4c2.76,0 5,-2.24 5,-5s-2.24,-5 -5,-5z";
    private static final String ICON_SETTINGS = "M19.14,12.94c0.04,-0.3 0.06,-0.61 0.06,-0.94c0,-0.32 -0.02,-0.64 -0.07,-0.94l2.03,-1.58c0.18,-0.14 0.23,-0.41 0.12,-0.61l-1.92,-3.32c-0.12,-0.22 -0.37,-0.29 -0.59,-0.22l-2.39,0.96c-0.5,-0.38 -1.03,-0.7 -1.62,-0.94L14.4,2.81c-0.04,-0.24 -0.24,-0.41 -0.48,-0.41h-3.84c-0.24,0 -0.43,0.17 -0.47,0.41L9.25,5.35C8.66,5.59 8.12,5.92 7.63,6.29L5.24,5.33c-0.22,-0.08 -0.47,0 -0.59,0.22L2.74,8.87C2.62,9.08 2.66,9.34 2.86,9.48l2.03,1.58C4.84,11.36 4.8,11.69 4.8,12s0.02,0.64 0.07,0.94l-2.03,1.58c-0.18,0.14 -0.23,0.41 -0.12,0.61l1.92,3.32c0.12,0.22 0.37,0.29 0.59,0.22l2.39,-0.96c0.5,0.38 1.03,0.7 1.62,0.94l0.36,2.54c0.05,0.24 0.24,0.41 0.48,0.41h3.84c0.24,0 0.44,-0.17 0.47,-0.41l0.36,-2.54c0.59,-0.24 1.13,-0.56 1.62,-0.94l2.39,0.96c0.22,0.08 0.47,0 0.59,-0.22l1.92,-3.32c0.12,-0.22 0.07,-0.47 -0.12,-0.61L19.14,12.94zM12,15.6c-1.98,0 -3.6,-1.62 -3.6,-3.6s1.62,-3.6 3.6,-3.6s3.6,1.62 3.6,3.6S13.98,15.6 12,15.6z";
    private static final String ICON_SEND = "M4,12l1.41,1.41L11,7.83V20h2V7.83l5.58,5.59L20,12l-8,-8 -8,8z";
    private static final String ICON_EXPAND = "M16.59,8.59L12,13.17 7.41,8.59 6,10l6,6 6,-6z";
    private static final String ICON_IMAGE = "M21,19V5c0,-1.1 -0.9,-2 -2,-2H5c-1.1,0 -2,0.9 -2,2v14c0,1.1 0.9,2 2,2h14c1.1,0 2,-0.9 2,-2zM8.5,13.5l2.5,3.01L14.5,12l4.5,6H5l3.5,-4.5z";
    private static final String ICON_FILE = "M14,2H6c-1.1,0 -1.99,0.9 -1.99,2L4,20c0,1.1 0.89,2 1.99,2H18c1.1,0 2,-0.9 2,-2V8l-6,-6zM16,18H8v-2h8v2zM16,14H8v-2h8v2zM13,9V3.5L18.5,9H13z";
    private static final String ICON_BULB = "M9,21c0,0.55 0.45,1 1,1h4c0.55,0 1,-0.45 1,-1v-1H9v1zM12,2C8.14,2 5,5.14 5,9c0,2.38 1.19,4.47 3,5.74V17c0,0.55 0.45,1 1,1h6c0.55,0 1,-0.45 1,-1v-2.26c1.81,-1.27 3,-3.36 3,-5.74 0,-3.86 -3.14,-7 -7,-7z";
    private static final String ICON_CHECK = "M9,16.17L4.83,12l-1.42,1.41L9,19 21,7l-1.41,-1.41z";

    private static final class Attachment {
        final String name;
        final boolean image;
        final String text; // isi file teks (null untuk foto / file biner)
        final boolean truncated;

        Attachment(String name, boolean image, String text, boolean truncated) {
            this.name = name;
            this.image = image;
            this.text = text;
            this.truncated = truncated;
        }
    }

    private FragmentAiChatBinding binding;
    private AiChatClient client;
    private AiSettingsStore store;
    private SharedPreferences uiPrefs;
    private final Handler main = new Handler(Looper.getMainLooper());
    private AiChatClient.Proposal pending;
    private final List<Attachment> attachments = new ArrayList<>();
    private boolean deepThinking;
    private PopupWindow popup;

    private final ActivityResultLauncher<String[]> pickFile = registerForActivityResult(
            new ActivityResultContracts.OpenDocument(), uri -> {
                if (uri != null) addAttachment(uri, false);
            });

    private final ActivityResultLauncher<String[]> pickPhoto = registerForActivityResult(
            new ActivityResultContracts.OpenDocument(), uri -> {
                if (uri != null) addAttachment(uri, true);
            });

    public static AiChatFragment newInstance(String scId, String javaName) {
        AiChatFragment f = new AiChatFragment();
        Bundle args = new Bundle();
        args.putString(ARG_SC_ID, scId);
        args.putString(ARG_JAVA_NAME, javaName);
        f.setArguments(args);
        return f;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentAiChatBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        Bundle args = getArguments();
        String scId = args != null ? args.getString(ARG_SC_ID, "") : "";
        String javaName = args != null ? args.getString(ARG_JAVA_NAME, "") : "";
        client = new AiChatClient(requireContext(), scId, javaName);
        store = new AiSettingsStore(requireContext());
        uiPrefs = requireContext().getSharedPreferences(UI_PREFS, Context.MODE_PRIVATE);
        deepThinking = uiPrefs.getBoolean(KEY_DEEP_THINKING, false);

        // Ikon digambar dari path (warna mengikuti app:tint di XML => ikut tema terang/gelap)
        binding.btnAttach.setImageDrawable(new PathIcon(ICON_LINK));
        binding.btnSettings.setImageDrawable(new PathIcon(ICON_SETTINGS));
        binding.ivSend.setImageDrawable(new PathIcon(ICON_SEND));
        binding.ivModelArrow.setImageDrawable(new PathIcon(ICON_EXPAND));

        binding.btnSend.setOnClickListener(v -> send());
        binding.btnApply.setOnClickListener(v -> confirmApply());
        binding.btnSettings.setOnClickListener(v -> openSettings());
        binding.btnAttach.setOnClickListener(v -> showAttachPopup());
        binding.btnModel.setOnClickListener(v -> showModelPopup());

        updateModelLabel();
        refreshAttachments();
    }

    @Override
    public void onResume() {
        super.onResume();
        // Pengguna mungkin baru mengubah provider / API key / model di AiSettingsActivity.
        if (binding != null) updateModelLabel();
    }

    @Override
    public void onDestroyView() {
        dismissPopup();
        binding = null;
        super.onDestroyView();
    }

    // ------------------------------------------------------------------ kirim pesan

    private void send() {
        String text = binding.etInput.getText() == null ? "" : binding.etInput.getText().toString().trim();
        if (text.isEmpty()) {
            return;
        }
        binding.etInput.setText("");

        StringBuilder log = new StringBuilder("Kamu: ").append(text);
        StringBuilder prompt = new StringBuilder();
        if (deepThinking) {
            prompt.append("[Deep thinking aktif: analisis masalah langkah demi langkah dan periksa ulang jawaban sebelum membalas.]\n\n");
            log.append("\n(Deep thinking)");
        }
        for (Attachment a : attachments) {
            log.append("\n📎 ").append(a.name);
            if (a.image) {
                prompt.append("[Foto terlampir: ").append(a.name).append(" - isi gambar belum bisa dikirim ke model]\n\n");
            } else if (a.text != null) {
                prompt.append("File: ").append(a.name).append("\n```\n").append(a.text);
                if (a.truncated) prompt.append("\n... (dipotong)");
                prompt.append("\n```\n\n");
            } else {
                prompt.append("[File terlampir: ").append(a.name).append(" - bukan file teks, isi tidak dibaca]\n\n");
            }
        }
        prompt.append(text);

        attachments.clear();
        refreshAttachments();
        appendLog(log.toString());
        setBusy(true);

        client.ask(prompt.toString(), new AiChatClient.Callback() {
            @Override
            public void onResult(String reply, List<AiChatClient.Proposal> proposals) {
                main.post(() -> {
                    if (binding == null) return;
                    setBusy(false);
                    appendLog("AI: " + reply);
                    if (!proposals.isEmpty()) {
                        pending = proposals.get(proposals.size() - 1);
                        binding.btnApply.setVisibility(View.VISIBLE);
                        appendLog("Usulan block (belum ditulis):\n" + pending.summary);
                    }
                });
            }

            @Override
            public void onError(String error) {
                main.post(() -> {
                    if (binding == null) return;
                    setBusy(false);
                    appendLog("Error: " + error);
                });
            }
        });
    }

    private void confirmApply() {
        if (pending == null) return;
        AiChatClient.Proposal p = pending;

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Tulis block?")
                .setMessage("Event: " + p.eventKey + "\n\n" + p.summary)
                .setPositiveButton("Tulis", (d, w) -> applyPending(p))
                .setNegativeButton("Batal", null)
                .show();
    }

    private void applyPending(AiChatClient.Proposal p) {
        String scId = getArguments() != null ? getArguments().getString(ARG_SC_ID, "") : "";
        new Thread(() -> {
            AiBlockWriter.Result r = AiBlockWriter.appendBlocks(scId, p.javaName, p.eventKey, p.steps);
            main.post(() -> {
                if (binding == null) return;
                appendLog(r.message);
                if (r.success) {
                    pending = null;
                    binding.btnApply.setVisibility(View.GONE);
                    SketchwareUtil.toast("Block ditulis.");
                }
            });
        }).start();
    }

    private void setBusy(boolean busy) {
        binding.btnSend.setEnabled(!busy);
        binding.btnSend.setAlpha(busy ? 0.5f : 1f);
        binding.progress.setVisibility(busy ? View.VISIBLE : View.GONE);
    }

    private void appendLog(String text) {
        binding.tvLog.append(text + "\n\n");
        binding.scrollLog.post(() -> {
            if (binding != null) binding.scrollLog.fullScroll(View.FOCUS_DOWN);
        });
    }

    // ------------------------------------------------------------------ setting & label model

    private void openSettings() {
        dismissPopup();
        startActivity(new Intent(requireContext(), AiSettingsActivity.class));
    }

    private void updateModelLabel() {
        int p = clampProvider(store.getProviderIndex());
        AiProviders.Provider provider = AiProviders.ALL[p];
        int m = store.getModelIndex();
        boolean hasKey = !store.getApiKey(p).isEmpty();
        if (m < 0 || m >= provider.models.length || !hasKey) {
            binding.tvModel.setText(hasKey ? provider.models[0] : "Pilih model");
        } else {
            binding.tvModel.setText(provider.models[m]);
        }
        updateAttachTint();
    }

    private int clampProvider(int i) {
        return Math.max(0, Math.min(i, AiProviders.ALL.length - 1));
    }

    /** Ikon attach memakai warna aksen kalau ada lampiran atau deep thinking aktif. */
    private void updateAttachTint() {
        boolean active = deepThinking || !attachments.isEmpty();
        binding.btnAttach.setImageTintList(ColorStateList.valueOf(themeColor(
                active ? com.google.android.material.R.attr.colorPrimary
                        : com.google.android.material.R.attr.colorOnSurfaceVariant)));
    }

    // ------------------------------------------------------------------ popup attach

    private void showAttachPopup() {
        dismissPopup();
        Context ctx = requireContext();
        int onSurface = themeColor(com.google.android.material.R.attr.colorOnSurface);
        int variant = themeColor(com.google.android.material.R.attr.colorOnSurfaceVariant);
        int primary = themeColor(com.google.android.material.R.attr.colorPrimary);

        LinearLayout box = new LinearLayout(ctx);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(6), dp(6), dp(6), dp(6));

        box.addView(menuRow(ICON_FILE, "Import file", onSurface, variant, v -> {
            dismissPopup();
            pickFile.launch(new String[]{"*/*"});
        }));
        box.addView(menuRow(ICON_IMAGE, "Foto", onSurface, variant, v -> {
            dismissPopup();
            pickPhoto.launch(new String[]{"image/*"});
        }));

        View divider = new View(ctx);
        divider.setBackgroundColor(themeColor(com.google.android.material.R.attr.colorOutline));
        divider.setAlpha(0.4f);
        LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(-1, dp(1));
        dlp.setMargins(dp(10), dp(4), dp(10), dp(4));
        box.addView(divider, dlp);

        // Deep thinking (di bawah import file & foto)
        LinearLayout think = menuRow(ICON_BULB, "Deep thinking", onSurface, deepThinking ? primary : variant, null);
        SwitchMaterial sw = new SwitchMaterial(ctx);
        sw.setChecked(deepThinking);
        sw.setClickable(false);
        think.addView(sw);
        think.setOnClickListener(v -> {
            deepThinking = !deepThinking;
            sw.setChecked(deepThinking);
            uiPrefs.edit().putBoolean(KEY_DEEP_THINKING, deepThinking).apply();
            ((ImageView) think.getChildAt(0)).setImageTintList(ColorStateList.valueOf(deepThinking ? primary : variant));
            updateAttachTint();
        });
        box.addView(think);

        showAbove(box, dp(250), false, false);
    }

    // ------------------------------------------------------------------ popup model

    private void showModelPopup() {
        dismissPopup();
        Context ctx = requireContext();
        int onSurface = themeColor(com.google.android.material.R.attr.colorOnSurface);
        int variant = themeColor(com.google.android.material.R.attr.colorOnSurfaceVariant);
        int primary = themeColor(com.google.android.material.R.attr.colorPrimary);

        int curProvider = clampProvider(store.getProviderIndex());
        int curModel = store.getModelIndex();

        LinearLayout list = new LinearLayout(ctx);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(dp(6), dp(6), dp(6), dp(6));

        boolean any = false;
        for (int i = 0; i < AiProviders.ALL.length; i++) {
            if (store.getApiKey(i).isEmpty()) continue; // hanya provider yang API key-nya sudah diisi
            any = true;
            final int pi = i;
            AiProviders.Provider provider = AiProviders.ALL[i];

            TextView header = new TextView(ctx);
            header.setText(provider.openAiCompatible ? provider.name : provider.name + " · belum didukung chat");
            header.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
            header.setTypeface(Typeface.DEFAULT_BOLD);
            header.setTextColor(variant);
            header.setPadding(dp(12), dp(10), dp(12), dp(4));
            list.addView(header);

            for (int j = 0; j < provider.models.length; j++) {
                final int mi = j;
                boolean selected = pi == curProvider && mi == curModel;
                LinearLayout row = new LinearLayout(ctx);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setMinimumHeight(dp(46));
                row.setPadding(dp(12), 0, dp(12), 0);
                setSelectableBackground(row);

                TextView name = new TextView(ctx);
                name.setText(provider.models[j]);
                name.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
                name.setTextColor(selected ? primary : onSurface);
                if (selected) name.setTypeface(Typeface.DEFAULT_BOLD);
                row.addView(name, new LinearLayout.LayoutParams(0, -2, 1f));

                if (selected) {
                    ImageView check = new ImageView(ctx);
                    PathIcon icon = new PathIcon(ICON_CHECK);
                    icon.setTint(primary);
                    check.setImageDrawable(icon);
                    row.addView(check, new LinearLayout.LayoutParams(dp(20), dp(20)));
                }

                if (provider.openAiCompatible) {
                    row.setOnClickListener(v -> {
                        store.setProviderIndex(pi);
                        store.setModelIndex(mi);
                        updateModelLabel();
                        dismissPopup();
                    });
                } else {
                    row.setAlpha(0.4f);
                }
                list.addView(row);
            }
        }

        if (!any) {
            TextView msg = new TextView(ctx);
            msg.setText("Belum ada API key. Isi API key provider di pengaturan AI untuk melihat model yang tersedia.");
            msg.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
            msg.setTextColor(variant);
            msg.setPadding(dp(12), dp(12), dp(12), dp(8));
            list.addView(msg);
            list.addView(menuRow(ICON_SETTINGS, "Buka pengaturan AI", onSurface, primary, v -> openSettings()));
        }

        ScrollView sv = new ScrollView(ctx);
        sv.setVerticalScrollBarEnabled(false);
        sv.addView(list);
        showAbove(sv, dp(300), true, true);
    }

    // ------------------------------------------------------------------ helper popup

    /** Menampilkan popup tepat di atas composer, gaya (warna/stroke) mengikuti tema aplikasi. */
    private void showAbove(View content, int preferredWidth, boolean alignRight, boolean scrollable) {
        Context ctx = requireContext();
        View anchor = binding.composer;
        int screenW = ctx.getResources().getDisplayMetrics().widthPixels;
        int screenH = ctx.getResources().getDisplayMetrics().heightPixels;
        int width = Math.min(preferredWidth, screenW - dp(24));

        content.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        int[] loc = new int[2];
        anchor.getLocationOnScreen(loc);
        int maxH = Math.min((int) (screenH * 0.6f), loc[1] - dp(40));
        int height = Math.min(content.getMeasuredHeight(), maxH);

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(themeColor(com.google.android.material.R.attr.colorSurface));
        bg.setCornerRadius(dp(20));
        bg.setStroke(dp(1), themeColor(com.google.android.material.R.attr.colorOutline));

        popup = new PopupWindow(content, width, height, true);
        popup.setBackgroundDrawable(bg);
        popup.setOutsideTouchable(true);
        popup.setElevation(dp(8));
        popup.setOnDismissListener(() -> popup = null);

        int xOff = alignRight ? anchor.getWidth() - width : 0;
        int yOff = -(anchor.getHeight() + height + dp(8));
        popup.showAsDropDown(anchor, xOff, yOff);
    }

    private void dismissPopup() {
        if (popup != null) {
            popup.dismiss();
            popup = null;
        }
    }

    private LinearLayout menuRow(String iconPath, String label, int textColor, int iconColor, View.OnClickListener click) {
        Context ctx = requireContext();
        LinearLayout row = new LinearLayout(ctx);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(dp(50));
        row.setPadding(dp(12), 0, dp(12), 0);
        setSelectableBackground(row);

        ImageView icon = new ImageView(ctx);
        PathIcon d = new PathIcon(iconPath);
        d.setTint(iconColor);
        icon.setImageDrawable(d);
        row.addView(icon, new LinearLayout.LayoutParams(dp(22), dp(22)));

        TextView tv = new TextView(ctx);
        tv.setText(label);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        tv.setTextColor(textColor);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1f);
        lp.setMarginStart(dp(14));
        row.addView(tv, lp);

        if (click != null) row.setOnClickListener(click);
        return row;
    }

    private void setSelectableBackground(View v) {
        TypedValue tv = new TypedValue();
        if (requireContext().getTheme().resolveAttribute(android.R.attr.selectableItemBackground, tv, true)) {
            v.setBackgroundResource(tv.resourceId);
        }
    }

    // ------------------------------------------------------------------ lampiran

    private void addAttachment(Uri uri, boolean image) {
        final Context app = requireContext().getApplicationContext();
        new Thread(() -> {
            ContentResolver cr = app.getContentResolver();
            String name = "file";
            try (Cursor c = cr.query(uri, null, null, null, null)) {
                if (c != null && c.moveToFirst()) {
                    int idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    if (idx >= 0) name = c.getString(idx);
                }
            } catch (Exception ignored) {
            }

            String text = null;
            boolean truncated = false;
            if (!image) {
                try (InputStream in = cr.openInputStream(uri)) {
                    if (in != null) {
                        ByteArrayOutputStream out = new ByteArrayOutputStream();
                        byte[] buf = new byte[8192];
                        int n;
                        while ((n = in.read(buf)) > 0 && out.size() < MAX_FILE_BYTES) {
                            out.write(buf, 0, Math.min(n, MAX_FILE_BYTES - out.size()));
                            if (out.size() >= MAX_FILE_BYTES) truncated = in.read() != -1;
                        }
                        byte[] data = out.toByteArray();
                        boolean binary = false;
                        for (byte b : data) {
                            if (b == 0) {
                                binary = true;
                                break;
                            }
                        }
                        if (!binary) text = new String(data, StandardCharsets.UTF_8);
                    }
                } catch (Exception ignored) {
                }
            }

            final Attachment att = new Attachment(name, image, text, truncated);
            main.post(() -> {
                if (binding == null) return;
                attachments.add(att);
                refreshAttachments();
            });
        }).start();
    }

    private void refreshAttachments() {
        binding.chipAttachments.removeAllViews();
        for (Attachment a : attachments) {
            Chip chip = new Chip(requireContext());
            chip.setText(a.name);
            chip.setSingleLine(true);
            chip.setCloseIconVisible(true);
            chip.setOnCloseIconClickListener(v -> {
                attachments.remove(a);
                refreshAttachments();
            });
            binding.chipAttachments.addView(chip);
        }
        binding.chipAttachments.setVisibility(attachments.isEmpty() ? View.GONE : View.VISIBLE);
        updateAttachTint();
    }

    // ------------------------------------------------------------------ util

    private int dp(float v) {
        return (int) (v * requireContext().getResources().getDisplayMetrics().density + 0.5f);
    }

    /** Ambil warna dari atribut tema aplikasi (ikut terang/gelap dan aksen yang dipakai). */
    private int themeColor(int attr) {
        Context ctx = requireContext();
        TypedValue tv = new TypedValue();
        if (ctx.getTheme().resolveAttribute(attr, tv, true)) {
            if (tv.type >= TypedValue.TYPE_FIRST_COLOR_INT && tv.type <= TypedValue.TYPE_LAST_COLOR_INT) {
                return tv.data;
            }
            if (tv.resourceId != 0) {
                return ContextCompat.getColor(ctx, tv.resourceId);
            }
        }
        return Color.GRAY;
    }

    /** Drawable ikon dari path data Material 24x24, mendukung tint (app:tint / setTint). */
    private static final class PathIcon extends Drawable {
        private final Path path;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private ColorStateList tint;
        private int alpha = 255;

        PathIcon(String pathData) {
            path = PathParser.createPathFromPathData(pathData);
            paint.setStyle(Paint.Style.FILL);
        }

        @Override
        public void draw(@NonNull Canvas canvas) {
            Rect b = getBounds();
            if (b.isEmpty()) return;
            int color = tint != null ? tint.getColorForState(getState(), tint.getDefaultColor()) : Color.BLACK;
            paint.setColor(color);
            paint.setAlpha(alpha * Color.alpha(color) / 255);
            int save = canvas.save();
            canvas.translate(b.left, b.top);
            canvas.scale(b.width() / 24f, b.height() / 24f);
            canvas.drawPath(path, paint);
            canvas.restoreToCount(save);
        }

        @Override
        public void setTintList(@Nullable ColorStateList tint) {
            this.tint = tint;
            invalidateSelf();
        }

        @Override
        public boolean isStateful() {
            return tint != null && tint.isStateful();
        }

        @Override
        protected boolean onStateChange(int[] state) {
            return tint != null && tint.isStateful();
        }

        @Override
        public void setAlpha(int a) {
            alpha = a;
            invalidateSelf();
        }

        @Override
        public void setColorFilter(@Nullable android.graphics.ColorFilter cf) {
            paint.setColorFilter(cf);
        }

        @Override
        public int getOpacity() {
            return PixelFormat.TRANSLUCENT;
        }
    }
}
