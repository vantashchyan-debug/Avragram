package com.avragramm.ui;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.view.View;

import com.avragramm.AvraConfig;
import com.avragramm.AvraIntro;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.R;
import org.telegram.messenger.Utilities;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalFragment;

import java.util.ArrayList;

import static org.telegram.messenger.LocaleController.formatString;
import static org.telegram.messenger.LocaleController.getString;

/** Fun → Launch screen: built-in video, own video or own photo with a duration, and a preview. */
public class AvraIntroActivity extends UniversalFragment {

    private static final int ID_SHUFFLE = 8;
    private static final int ID_ENABLED = 1, ID_DEFAULT = 2, ID_VIDEO = 3, ID_PHOTO = 4,
            ID_PREVIEW = 5, ID_RESET = 6;
    private static final int REQ_VIDEO = 7731, REQ_PHOTO = 7732;
    private static final int MAX_SEC = 10;

    private boolean importing;

    @Override
    protected CharSequence getTitle() {
        return getString(R.string.AvraIntroScreen);
    }

    private static boolean hasVideo() {
        return AvraIntro.customVideo().length() > 0;
    }

    private static boolean hasPhoto() {
        return AvraIntro.customPhoto().length() > 0;
    }

    @Override
    protected void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        AvraConfig.ensureLoaded();
        items.add(UItem.asCheck(ID_ENABLED, getString(R.string.AvraIntro), getString(R.string.AvraIntroInfo), true).setChecked(AvraConfig.introVideo));
        items.add(UItem.asShadow(null));

        int type = AvraConfig.introType;
        if (type == AvraConfig.INTRO_VIDEO && !hasVideo() || type == AvraConfig.INTRO_PHOTO && !hasPhoto()) type = AvraConfig.INTRO_DEFAULT;
        items.add(UItem.asHeader(getString(R.string.AvraIntroWhat)));
        items.add(UItem.asRadio(ID_DEFAULT, getString(R.string.AvraIntroDefault)).setChecked(type == AvraConfig.INTRO_DEFAULT));
        items.add(UItem.asRadio(ID_VIDEO, getString(R.string.AvraIntroOwnVideo),
                getString(hasVideo() ? R.string.AvraIntroChange : R.string.AvraIntroPick)).setChecked(type == AvraConfig.INTRO_VIDEO));
        items.add(UItem.asRadio(ID_PHOTO, getString(R.string.AvraIntroOwnPhoto),
                getString(hasPhoto() ? R.string.AvraIntroChange : R.string.AvraIntroPick)).setChecked(type == AvraConfig.INTRO_PHOTO));
        items.add(UItem.asShadow(getString(importing ? R.string.AvraIntroImporting : R.string.AvraIntroWhatInfo)));

        if (type == AvraConfig.INTRO_DEFAULT) {
            items.add(UItem.asCheck(ID_SHUFFLE, getString(R.string.AvraIntroShuffle), formatString(R.string.AvraIntroShuffleInfo, AvraIntro.VIDEOS.length), true).setChecked(AvraConfig.introShuffle));
            items.add(UItem.asShadow(null));
        }

        if (type == AvraConfig.INTRO_PHOTO) {
            items.add(UItem.asHeader(getString(R.string.AvraIntroDuration)));
            String[] choices = new String[MAX_SEC];
            for (int i = 0; i < MAX_SEC; i++) choices[i] = formatString(R.string.AvraIntroSeconds, i + 1);
            int chosen = Math.max(0, Math.min(MAX_SEC - 1, AvraConfig.introPhotoSec - 1));
            items.add(UItem.asSlideView(choices, chosen, (Utilities.Callback<Integer>) index -> {
                AvraConfig.introPhotoSec = index + 1;
                AvraConfig.save();
            }));
            items.add(UItem.asShadow(getString(R.string.AvraIntroDurationInfo)));
        }

        items.add(UItem.asButton(ID_PREVIEW, R.drawable.msg_view_file, getString(R.string.AvraIntroPreview)));
        if (hasVideo() || hasPhoto()) {
            items.add(UItem.asButton(ID_RESET, R.drawable.msg_delete, getString(R.string.AvraIntroReset)).red());
        }
        items.add(UItem.asShadow(null));
    }

    private void setType(int type) {
        AvraConfig.introType = type;
        AvraConfig.save();
        listView.adapter.update(true);
    }

    private void pick(boolean photo) {
        Activity activity = getParentActivity();
        if (activity == null) return;
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType(photo ? "image/*" : "video/*");
        try {
            startActivityForResult(Intent.createChooser(intent, getString(photo ? R.string.AvraIntroOwnPhoto : R.string.AvraIntroOwnVideo)), photo ? REQ_PHOTO : REQ_VIDEO);
        } catch (Exception e) {
            BulletinFactory.of(this).createErrorBulletin(getString(R.string.AvraIntroPickFailed)).show();
        }
    }

    @Override
    public void onActivityResultFragment(int requestCode, int resultCode, Intent data) {
        if (requestCode != REQ_VIDEO && requestCode != REQ_PHOTO) return;
        if (resultCode != Activity.RESULT_OK || data == null || data.getData() == null) return;
        final boolean photo = requestCode == REQ_PHOTO;
        final Uri uri = data.getData();
        importing = true;
        if (listView != null) listView.adapter.update(true);
        Utilities.globalQueue.postRunnable(() -> {
            boolean ok = AvraIntro.importFile(uri, photo);
            AndroidUtilities.runOnUIThread(() -> {
                importing = false;
                if (ok) {
                    AvraConfig.introType = photo ? AvraConfig.INTRO_PHOTO : AvraConfig.INTRO_VIDEO;
                    AvraConfig.introVideo = true;
                    AvraConfig.save();
                    BulletinFactory.of(this).createSimpleBulletin(R.raw.contact_check, getString(R.string.AvraIntroSaved)).show();
                } else {
                    BulletinFactory.of(this).createErrorBulletin(getString(R.string.AvraIntroPickFailed)).show();
                }
                if (listView != null) listView.adapter.update(true);
            });
        });
    }

    @Override
    protected void onClick(UItem item, View view, int position, float x, float y) {
        switch (item.id) {
            case ID_ENABLED:
                AvraConfig.introVideo = !AvraConfig.introVideo;
                AvraConfig.save();
                listView.adapter.update(true);
                break;
            case ID_SHUFFLE:
                AvraConfig.introShuffle = !AvraConfig.introShuffle;
                AvraConfig.save();
                listView.adapter.update(true);
                break;
            case ID_DEFAULT:
                setType(AvraConfig.INTRO_DEFAULT);
                break;
            case ID_VIDEO:
                if (AvraConfig.introType == AvraConfig.INTRO_VIDEO && hasVideo() || !hasVideo()) {
                    pick(false);
                } else {
                    setType(AvraConfig.INTRO_VIDEO);
                }
                break;
            case ID_PHOTO:
                if (AvraConfig.introType == AvraConfig.INTRO_PHOTO && hasPhoto() || !hasPhoto()) {
                    pick(true);
                } else {
                    setType(AvraConfig.INTRO_PHOTO);
                }
                break;
            case ID_PREVIEW:
                AvraIntro.preview(getParentActivity());
                break;
            case ID_RESET:
                AvraIntro.customVideo().delete();
                AvraIntro.customPhoto().delete();
                setType(AvraConfig.INTRO_DEFAULT);
                break;
        }
    }

    @Override
    protected boolean onLongClick(UItem item, View view, int position, float x, float y) {
        if (item.id == ID_VIDEO) {
            pick(false);
            return true;
        } else if (item.id == ID_PHOTO) {
            pick(true);
            return true;
        }
        return false;
    }
}
