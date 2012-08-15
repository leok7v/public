package embedjs;

import java.io.*;
import java.security.*;

public class io {

    private static final char INCLUDE = '_'; // prefix

    public static boolean isDirectory(File d) {
        try {
            return d != null &&  d.isDirectory();
        } catch (AccessControlException x) {
            return false;
        }
    }

    public static boolean isFile(File f) {
        try {
            return f != null && f.isFile();
        } catch (AccessControlException x) {
            return false;
        }
    }


    public static boolean isIncludeFile(File f) {
        String n = f.getName();
        return n.length() > 0 && n.charAt(0) == INCLUDE;
    }

    public static byte[] readFully(File f) {
        if (isIncludeFile(f)) {
            return combine(f);
        } else {
            return readFileContentFully(f);
        }
    }

    private static byte[] readFileContentFully(File f) {
        FileInputStream s = null;
        try {
            s = new FileInputStream(f);
            return readFully(s);
        } catch (IOException e) {
            throw new Error(e);
        } finally {
            close(s);
        }
    }

    public static void close(InputStream s) {
        try {
            if (s != null) {
                s.close();
            }
        } catch (IOException e) {
            throw new Error(e);
        }
    }

    public static String getMimeTypeFromFilename(String name) {
        name = name.toLowerCase();
        if (name.endsWith(".txt")) return "text/plain";
        if (name.endsWith(".css")) return "text/css";
        if (name.endsWith(".ico")) return "image/x-icon";
        if (name.endsWith(".png")) return "image/png";
        if (name.endsWith(".gif")) return "image/gif";
        if (name.endsWith(".jpg")) return "image/jpeg";
        if (name.endsWith(".jpeg")) return "image/jpeg";
        if (name.endsWith(".html")) return "text/html";
        if (name.endsWith(".htm")) return "text/html";
        if (name.endsWith(".xhtml")) return "application/xhtml+xml";
        if (name.endsWith(".js")) return "application/javascript";
        if (name.endsWith(".dmg")) return "application/x-apple-diskimage";
        if (name.endsWith(".exe")) return "application/octet-stream";
        if (name.endsWith(".ttf")) return "application/octet-stream" /*"application/x-font-truetype"*/;
        if (name.endsWith(".otf")) return "application/octet-stream" /*"application/x-font-opentype"*/;
        if (name.endsWith(".woff")) return "application/octet-stream" /* "application/x-font-woff" */;
        if (name.endsWith(".eof")) return "application/vnd.ms-fontobject";
        if (name.endsWith(".svg")) return "image/svg+xml";
        return "application/octet-stream"; // default to binary resource
    }

    private static byte[] combine(File f) {
        String s = new String(readFileContentFully(f));
        String[] lines = s.split("\\r?\\n");
        int total = 0;
        for (String line : lines) {
            String t = line.trim();
            if (t.length() > 0 && !t.startsWith("#")) {
                File i = new File(f.getParentFile(), t);
                if (i.exists()) {
                    total += i.length();
                } else {
                    throw new Error("file " + i + " not found.");
                }
            }
        }
        byte[] content = new byte[total];
        int offset = 0;
        for (String line : lines) {
            String t = line.trim();
            if (t.length() > 0 && !t.startsWith("#")) {
                File i = new File(f.getParentFile(), t);
                byte[] buf = readFileContentFully(i);
                System.arraycopy(buf, 0, content, offset, buf.length);
                offset += buf.length;
            }
        }
        return content;
    }

    public static byte[] readFully(InputStream is) {
        try {
            int len = Math.max(4096, is.available());
            ByteArrayOutputStream baos = new ByteArrayOutputStream(len);
            byte[] buf = new byte[len];
            for (;;) {
                int k = is.read(buf);
                if (k <= 0) {
                    return baos.toByteArray();
                }
                baos.write(buf, 0, k);
            }
        } catch (IOException e) {
            throw new Error(e);
        }
    }

}
