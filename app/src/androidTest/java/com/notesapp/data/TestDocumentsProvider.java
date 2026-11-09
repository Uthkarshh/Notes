package com.notesapp.data;

import android.database.Cursor;
import android.database.MatrixCursor;
import android.os.CancellationSignal;
import android.os.ParcelFileDescriptor;
import android.provider.DocumentsContract.Document;
import android.provider.DocumentsContract.Root;
import android.provider.DocumentsProvider;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;

/** A real SAF provider confined to cache fixtures. No Kotlin runtime is required in its separate process. */
public class TestDocumentsProvider extends DocumentsProvider {
    private File canonical(File value) {
        try { return value.getCanonicalFile(); } catch (IOException e) { throw new IllegalArgumentException(e); }
    }
    private File root() {
        File value = canonical(new File(getContext().getCacheDir(), "phase34-documents"));
        if (!value.isDirectory() && !value.mkdirs()) throw new IllegalStateException("Cannot create fixture root");
        return value;
    }
    private File file(String id) {
        if (!id.equals("root") && !id.startsWith("root/")) throw new IllegalArgumentException("Invalid fixture id");
        File root = root();
        File value = id.equals("root") ? root : canonical(new File(root, id.substring(5)));
        if (!value.equals(root) && !value.getPath().startsWith(root.getPath() + File.separator)) throw new IllegalArgumentException("Fixture outside test root");
        return value;
    }
    private String id(File file) { File root = root(); return file.equals(root) ? "root" : "root/" + file.getPath().substring(root.getPath().length() + 1); }
    @Override public boolean onCreate() { return true; }
    @Override public Cursor queryRoots(String[] projection) {
        MatrixCursor result = new MatrixCursor(projection != null ? projection : new String[] { Root.COLUMN_ROOT_ID, Root.COLUMN_DOCUMENT_ID, Root.COLUMN_TITLE, Root.COLUMN_FLAGS });
        MatrixCursor.RowBuilder row = result.newRow();
        for (String column : result.getColumnNames()) {
            Object value = column.equals(Root.COLUMN_ROOT_ID) ? "notes-test" : column.equals(Root.COLUMN_DOCUMENT_ID) ? "root" : column.equals(Root.COLUMN_TITLE) ? "Notes test storage" : column.equals(Root.COLUMN_FLAGS) ? Root.FLAG_SUPPORTS_CREATE : null;
            row.add(column, value);
        }
        return result;
    }
    private Cursor documents(File[] files, String[] projection) {
        MatrixCursor result = new MatrixCursor(projection != null ? projection : new String[] { Document.COLUMN_DOCUMENT_ID, Document.COLUMN_DISPLAY_NAME, Document.COLUMN_MIME_TYPE, Document.COLUMN_FLAGS, Document.COLUMN_SIZE });
        if (files != null) for (File file : files) {
            MatrixCursor.RowBuilder row = result.newRow();
            for (String column : result.getColumnNames()) {
                Object value = null;
                if (column.equals(Document.COLUMN_DOCUMENT_ID)) value = id(file);
                else if (column.equals(Document.COLUMN_DISPLAY_NAME)) value = file.getName();
                else if (column.equals(Document.COLUMN_MIME_TYPE)) value = file.isDirectory() ? Document.MIME_TYPE_DIR : "application/octet-stream";
                else if (column.equals(Document.COLUMN_FLAGS)) value = file.isDirectory() ? Document.FLAG_DIR_SUPPORTS_CREATE | Document.FLAG_SUPPORTS_DELETE : Document.FLAG_SUPPORTS_WRITE | Document.FLAG_SUPPORTS_DELETE;
                else if (column.equals(Document.COLUMN_SIZE)) value = file.length();
                else if (column.equals(Document.COLUMN_LAST_MODIFIED)) value = file.lastModified();
                row.add(column, value);
            }
        }
        return result;
    }
    @Override public Cursor queryDocument(String documentId, String[] projection) { return documents(new File[] { file(documentId) }, projection); }
    @Override public Cursor queryChildDocuments(String parentDocumentId, String[] projection, String sortOrder) { return documents(file(parentDocumentId).listFiles(), projection); }
    @Override public ParcelFileDescriptor openDocument(String documentId, String mode, CancellationSignal signal) throws FileNotFoundException { return ParcelFileDescriptor.open(file(documentId), ParcelFileDescriptor.parseMode(mode)); }
    @Override public String createDocument(String parentDocumentId, String mimeType, String displayName) throws FileNotFoundException {
        if (displayName.isEmpty() || displayName.contains("/") || displayName.contains("\\") || displayName.equals(".") || displayName.equals("..")) throw new IllegalArgumentException("Invalid fixture name");
        File parent = file(parentDocumentId);
        File output = new File(parent, displayName);
        int n = 1;
        while (output.exists()) output = new File(parent, displayName + "-" + n++);
        try {
            if (!(mimeType.equals(Document.MIME_TYPE_DIR) ? output.mkdir() : output.createNewFile())) throw new FileNotFoundException("Cannot create fixture");
        } catch (IOException e) { throw new FileNotFoundException(e.getMessage()); }
        return id(output);
    }
    private void delete(File target) throws FileNotFoundException {
        File[] children = target.listFiles();
        if (children != null) for (File child : children) delete(child);
        if (!target.delete()) throw new FileNotFoundException("Cannot delete fixture");
    }
    @Override public void deleteDocument(String documentId) throws FileNotFoundException {
        File target = file(documentId);
        if (target.equals(root())) throw new IllegalArgumentException("Cannot delete the test root");
        delete(target);
    }
    @Override public boolean isChildDocument(String parentDocumentId, String documentId) { return file(documentId).getPath().startsWith(file(parentDocumentId).getPath() + File.separator); }
}
