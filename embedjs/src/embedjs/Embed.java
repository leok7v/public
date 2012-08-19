package embedjs;

import com.google.common.util.*;

import java.io.*;
import java.util.*;
import java.util.regex.*;

/**
 This is oversimplified regex based embedding of javascript,
 css and data uri.
 It may produce crippled results unless used with caution.
 However more complex solutions are short of writing
 complete javascript and css parser.

 Optional
    http://developer.yahoo.com/yui/compressor/
    java -jar yuicompressor-x.y.z.jar myfile.js -o myfile-min.js --charset utf-8

    https://github.com/douglascrockford/JSLint

    http://code.google.com/p/jslint4java/

*/

public class Embed {

    private final static int BUFFER_SIZE = 1024 * 1204;

    private static File src_dir;
    private static File src_file;

    public static void main(String[] a) {
        if (a.length < 2) {
            System.err.println("usage: jar -jar embedjs.jar <src_dir> <dest_dir>\n" +
                    "or\n" +
                    "jar2bin embedjs.jar\n" +
                    "embedjs <scr> <dest>\n");
            System.exit(1);
        }
        src_dir = io.getCanonicalFile(a[0]);
        File dst_dir = io.getCanonicalFile(a[1]);
        if (!src_dir.isDirectory()) {
            error(src_dir + " does not exist or is not a directory");
        }
        if (io.getCanonicalPath(dst_dir).equalsIgnoreCase(io.getCanonicalPath(src_dir))) {
            error(dst_dir + " cannot be the same as " + src_dir);
        }
        if (!io.isDirectory(dst_dir) && !dst_dir.mkdirs()) {
            error("Failed to locate or create directory: " + dst_dir);
        }
        File[] files = src_dir.listFiles();
        if (files == null) {
            error("Nothing to do. No *.html resources found at: " + src_dir);
            return;
        }

        for (File f : files) {
            String name = f.getName().toLowerCase();
            if (name.endsWith(".html") || name.endsWith(".jsp")) {
                try {
                    src_file = new File(dst_dir, f.getName());
                    page.clear();
                    embed(src_file, new String(io.readFully(f)));
                } catch (IOException e) {
                    e.printStackTrace();
                    error(e.getMessage());
                }
            }
        }
    }

    private static void embed(File out, String s) throws IOException {
        s = stripComments(out, s); // IMPORTANT: strip comments before embedding
        s = embedStyles(s);
        s = embedScripts(s);
        s = embedImages(s);
        OutputStream os = new FileOutputStream(out);
        try {
            os.write(s.getBytes());
        } finally {
            os.close();
        }
    }

    private static void warnCannotEmbed(File f) {
        if (f != null && !f.getName().isEmpty()) {
            System.err.println("WARNING: file " + f + " cannot be embedded.");
        }
    }

    private static void error(String s) {
        System.err.println("ERROR: " + s);
        System.exit(1);
    }

    private static String embedScripts(String s) throws IOException {
        StringBuilder sb = new StringBuilder(BUFFER_SIZE);
        Matcher m1 = Pattern.compile("<script([^<]*)/>").matcher(s);
        Matcher m2 = Pattern.compile("<script([^<]*)</script>").matcher(s);
        Matcher m;
        if (m1.find() && m2.find()) {
            m = m1.start() < m2.start() ? m1 : m2;
        } else {
            m = m1.find() ? m1 : m2;
        }
        m.reset();
        int i = 0;
        while (m.find(i)) {
            String src = valueOf("src", m.group(0));
            File f = locateFile(src);
            if (io.isFile(f)) {
                sb.append(s.substring(i, m.start()));
                String c = compress(f);
                sb.append("\n<script type=\"text/javascript\" >\n").append(c).append("\n</script>\n");
            } else {
                warnCannotEmbed(f);
                sb.append(s.substring(i, m.end()));
            }
            i = m.end();
        }
        return i == 0 ? s : sb.append(s.substring(i, s.length())).toString();
    }

    private static String compress(File f) {
        String n = f.getName().toLowerCase();
        if (n.contains(".min.") || !(n.endsWith(".js") || n.endsWith(".css"))) {
            return new String(io.readFully(f)); // do not cripple already minified javascript
        }
        String b = f.getName().substring(0, f.getName().lastIndexOf('.'));
        String x = f.getName().substring(f.getName().lastIndexOf('.'));
        File t = new File(f.getParentFile(), b + ".min" + x);
        if (t.exists()) {
            error("file " + t + " already exists");
        }
        System.err.println("compile: " + io.getCanonicalPath(f));
        String[] args = new String[]{/*"-v", */"--charset", "utf-8", "-o",
                io.getCanonicalPath(t), io.getCanonicalPath(f)};
        com.yahoo.platform.yui.compressor.YUICompressor.main(args);
        String r = new String(io.readFully(t));
        if (!t.delete()) {
            error("failed to delete file " + t);
        }
        return r;
    }

    private static String stripComments(File f, String s) throws IOException {
        if (f.getName().toLowerCase().contains(".min.")) {
            return s; // do not cripple already minified javascript
        }
        // see: http://ostermiller.org/findcomment.html
        return s.replaceAll("(<!--(?:.|[\\n\\r])*?-->)|" +
                            "(/\\*(?:.|[\\n\\r])*?\\*/)", "");
        // there is actually no safe way to strip "//" comment considering something like:
        // return "//foo"; // bar "
        // or even more complicated situations
    }

    private static String embedImages(String s) throws IOException {
        StringBuilder sb = new StringBuilder(BUFFER_SIZE);
        int i = 0;
        Matcher m = Pattern.compile("<img([^<]*)/>").matcher(s);
        while (m.find(i)) {
            String src = valueOf("src", m.group(0));
            File f = locateFile(src);
            if (f.isFile()) {
                sb.append(s.substring(i, m.start()));
                String c = base64EncodedFileForCss(f.getAbsolutePath());
                sb.append("\n<img src=\"").append(c).append("\" />");
            } else {
                warnCannotEmbed(f);
                sb.append(s.substring(i, m.end()));
            }
            i = m.end();
        }
        return i == 0 ? s : sb.append(s.substring(i, s.length())).toString();
    }

    private static String embedStyles(String s) throws IOException {
        StringBuilder sb = new StringBuilder(BUFFER_SIZE);
        Matcher m = Pattern.compile("<link([^<]*)/>").matcher(s);
        int i = 0;
        while (m.find(i)) {
            String g = m.group(0);
            // DO NOT embed rel="apple-touch..." links
            String href = g.toLowerCase().contains("stylesheet") && g.toLowerCase().contains("rel=") ?
                    valueOf("href", g) : null;
            File f = locateFile(href);
            if (f.isFile()) {
                sb.append(s.substring(i, m.start()));
                String c = compress(f);
                // data uri can have "//" thus strip comment before not after
                c = encodeDataUris(f, c);
                sb.append("\n<style type=\"text/css\">\n").append(c).append("\n</style>\n");
            } else {
                warnCannotEmbed(f);
                sb.append(s.substring(i, m.end()));
            }
            i = m.end();
        }
        return i == 0 ? s : sb.append(s.substring(i, s.length())).toString();
    }


    private static String valueOf(String attr, String s) {
        String lc = s.toLowerCase();
        String a = (attr + '=').toLowerCase();
        int start = lc.indexOf(a);
        if (start < 0 || start >= s.length() - a.length()) {
            return null;
        }
        char q = s.charAt(start + a.length());
        if (q == '\"' || q == '\'') {
            int end = s.indexOf(q, start + a.length() + 1);
            if (end <= start + a.length()) {
                return null;
            }
            return s.substring(start + a.length() + 1, end);
        } else {
            int end = start + a.length();
            while (end < s.length() && !Character.isWhitespace(s.charAt(end)) && s.charAt(end) != '>') {
                end++;
            }
            return end < s.length() ? s.substring(start + a.length(), end) : null;
        }
    }

    private static File locateFile(File base, String fn) {
        if (fn != null) {
            fn = unquote(fn.trim()).trim();
            if (fn.startsWith("data:") && fn.contains(";base64,") ||
                fn.startsWith("http://") || fn.startsWith("https://") || fn.startsWith("//")) {
                // ignore
            } else {
                File f = base != null ? new File(base, fn) : null;
                if (f != null && f.isFile()) {
                    return f;
                } else {
                    f = new File(src_dir, fn);
                    return f.isFile() ? f : new File(fn);
                }
            }
        }
        return new File("");
    }

    private static File locateFile(String fn) {
        File f = locateFile(null, fn);
        return io.isFile(f) ? io.getCanonicalFile(f) : f;
    }

    private static String encodeDataUris(File css, String s) {
        StringBuilder sb = new StringBuilder(BUFFER_SIZE);
        Matcher m = Pattern.compile("url\\(([^)]*)\\)").matcher(s);
        int i = 0;
        while (m.find(i)) {
            String fn = m.group(0);
            fn = fn.substring(4, fn.length() - 1);
            File f = locateFile(css.getParentFile(), fn);
            if (f.isFile()) {
                sb.append(s.substring(i, m.start()));
                String c = "url(" + base64EncodedFileForCss(f.getAbsolutePath()) + ")";
                sb.append(c);
            } else {
                warnCannotEmbed(f);
                sb.append(s.substring(i, m.end()));
            }
            i = m.end();
        }
        return i == 0 ? s : sb.append(s.substring(i, s.length())).toString();
    }

    private static String unquote(String s) {
        // it is possible to write <script src=jquery.js></script> w/o quotes
        if (s != null) {
            s = s.trim();
            if (s.startsWith("\"") && s.endsWith("\"") || s.startsWith("\'") && s.endsWith("\'")) {
                s = s.substring(1, s.length() - 1);
            }
        }
        return s;
    }

    private static HashMap<String, String> cache = new HashMap<String, String>(256);
    private static HashMap<String, String> site  = new HashMap<String, String>(256);
    private static HashSet<String> page  = new HashSet<String>(256);

    private static String base64EncodedFileForCss(String path) {
        path = io.getCanonicalPath(path);
        String r = cache.get(path);
        if (r == null) {
            File f = io.getCanonicalFile(path);
            byte[] bytes = io.readFully(f);
            System.err.println("embedded " + path + " size " + formatSize(f.length()));
            String mime = io.getMimeTypeFromFilename(f.getName());
            r = "data:" + mime + ";base64," + Base64.encode(bytes);
            cache.put(path, r);
            page.add(path);
            site.put(path, src_file.getAbsolutePath());
        } else {
            if (page.contains(path)) {
                System.err.println("WARNING: duplicate resource " + path + " in " + src_file);
            } else {
                System.err.println("WARNING: while processing " + src_file +
                        " embedded " + path + " that has been already used in " + site.get(path));
            }
        }
        return r;
    }

    private static String formatSize(long length) {
        if (length < 1024) {
            return length + " bytes";
        } else {
            return (int)((length / 1024.) * 10) / 10. + " KB";
        }
    }

}
