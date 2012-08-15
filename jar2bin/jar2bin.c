#include <stdio.h>
#include <string.h>
#include <stdlib.h>
#include <unistd.h>

static void error(char* s) {
    fprintf(stderr, "%s", s);
    exit(1);
}

/* */

int main(int argc, const char * argv[]) {
    char* last = strrchr(argv[0], '/');
    if (last == NULL) {
        last = (char*)argv[0];
    } else {
        last++;
    }
    char* home = getenv("HOME");
    char* jar_name = malloc(strlen(home) + strlen(last) + strlen("bin") + 1024);
    strcpy(jar_name, home);
    strcat(jar_name, "/bin/.");
/*
    fprintf(stderr, "argc=%d\n", argc);
    fprintf(stderr, "last=%s\n", last);
*/
    if (strcmp(last, "jar2bin") == 0) {
        if (argc < 2) {
            error("usage: jar2bin <file.jar>\n");
        }
        char* short_jar = (char*)argv[1];
        if (strrchr(short_jar, '/') != NULL) {
            short_jar = strrchr(short_jar, '/') + 1;
        }
        strcat(jar_name, short_jar);
        char* short_bin = strdup(short_jar);
        if (strrchr(short_bin, '.') == NULL) {
            error("jar_file has to have .jar suffix");
        }
        *strrchr(short_bin, '.') = 0;
        char* run = malloc(strlen(jar_name) + strlen(argv[1]) + 16);
        strcpy(run, "cp ");
        strcat(run, argv[1]);
        strcat(run, " ");
        strcat(run, jar_name);
        if (system(run) != 0) {
            error("faile to copy jar file");
        }
        char* bin_name = malloc(strlen(home) + strlen(short_jar) + strlen("bin") + 1024);
        strcpy(bin_name, home);
        strcat(bin_name, "/bin/");
        strcat(bin_name, short_bin);
        run = malloc(strlen(bin_name) + strlen(argv[0]) + 16);
        strcpy(run, "ln -f ");
        strcat(run, argv[0]);
        strcat(run, " ");
        strcat(run, bin_name);
        if (system(run) != 0) {
            error("failed to link binary");
        }
    } else {
        strcat(jar_name, last);
        strcat(jar_name, ".jar");
/*      fprintf(stderr, "jar_name=%s\n", jar_name); */
        if (access(jar_name, R_OK) != 0) {
/*          fprintf(stderr, "access=%d\n", access(jar_name, R_OK)); */
            error("ERROR: cannot access ~/bin/.file.jar\n\tinstall .file.jar into ~/bin/ using install.jar\n");
        } else {
            int size = (argc + 3)  * sizeof(char*);
            char** args = malloc(size);
            memset(args, 0, size);
            int i = 0;
            args[i++] = "-Xmx1024m";
            args[i++] = "-jar";
            args[i++] = jar_name;
            for (int k = 1; k < argc; k++) {
                args[i++] = (char*)argv[k];
            }
            if (execv("/usr/bin/java", args) != 0) {
                error("cannot start /usr/bin/java. check your $JAVA_HOME and /usr/bin/java\n");
            }
        }
    }
    return 0;
}

