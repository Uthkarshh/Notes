package com.notesapp.data;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.provider.DocumentsContract;
import java.io.File;
import java.util.UUID;

/** Framework-only because this component runs in the test APK's own process, outside instrumentation. */
public class TestGrantReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        File root = new File(context.getCacheDir(), "phase34-documents");
        if (!root.isDirectory() && !root.mkdirs()) throw new IllegalStateException("Cannot create fixture root");
        String name = "Test-" + UUID.randomUUID();
        File fixture = new File(root, name);
        if (!fixture.mkdir()) throw new IllegalStateException("Cannot create fixture tree");
        Uri tree = DocumentsContract.buildTreeDocumentUri("com.notesapp.test.documents", "root/" + name);
        context.grantUriPermission("com.notesapp", tree, Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_PREFIX_URI_PERMISSION);
        setResultData(tree.toString());
    }
}
