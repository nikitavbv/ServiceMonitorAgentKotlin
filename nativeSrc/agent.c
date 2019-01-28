#include <curl.h>
#include <mysql.h>

struct MemoryStruct {
    char *memory;
    size_t size;
};

static size_t writeMemoryCallback(void *contents, size_t size, size_t nmemb, void *userp) {
    size_t realSize = size * nmemb;
    struct MemoryStruct* mem = (struct MemoryStruct*) userp;

    char *ptr = realloc(mem->memory, mem->size + realSize + 1);
    if (ptr == NULL) {
        printf("not enough memory (realloc returned NULL)\n");
        return 0;
    }

    mem->memory = ptr;
    memcpy(&(mem->memory[mem->size]), contents, realSize);
    mem->size += realSize;
    mem->memory[mem->size] = 0;

    return realSize;
}

const char* makeHTTPRequest(const char* url, const char* method, const char* data) {
    CURL *curl_handle;
    CURLcode res;

    struct MemoryStruct chunk;
    chunk.memory = malloc(1); // will be grown as needed
    chunk.size = 0; // no data ath this point

    curl_handle = curl_easy_init();
    if (!curl_handle) {
        fprintf(stderr, "failed to init curl");
        return NULL;
    }

    struct curl_slist* headers = NULL;
    curl_easy_setopt(curl_handle, CURLOPT_URL, url);

    if (strcmp("POST", method) == 0) {
        curl_easy_setopt(curl_handle, CURLOPT_POSTFIELDS, data);
        headers = curl_slist_append(headers, "Content-Type: application/json");
    } else if (strcmp("PUT", method) == 0) {
        curl_easy_setopt(curl_handle, CURLOPT_CUSTOMREQUEST, "PUT");
        curl_easy_setopt(curl_handle, CURLOPT_POSTFIELDS, data);
        headers = curl_slist_append(headers, "Content-Type: application/json");
    }

    curl_easy_setopt(curl_handle, CURLOPT_USERAGENT, "ServiceMonitorAgent/1.0");
    curl_easy_setopt(curl_handle, CURLOPT_HTTPHEADER, headers);

    curl_easy_setopt(curl_handle, CURLOPT_WRITEFUNCTION, writeMemoryCallback);
    curl_easy_setopt(curl_handle, CURLOPT_WRITEDATA, (void *)&chunk);

    res = curl_easy_perform(curl_handle);
    if (res != CURLE_OK) {
        fprintf(stderr, "curl_easy_perform() failed: %s\n", curl_easy_strerror(res));
        curl_easy_cleanup(curl_handle);
        return NULL;
    }

    curl_easy_cleanup(curl_handle);
    return chunk.memory;
}

long int getCurrentTimeMillis() {
    struct timeval tp;
    gettimeofday(&tp, NULL);
    return tp.tv_sec * 1000 + tp.tv_usec / 1000;
}

char* getCurrentTimeRFC3339() {
    time_t now;
    time(&now);
    struct tm *p = localtime(&now);
    char buf[100];
    size_t len = strftime(buf, sizeof buf - 1, "%FT%T%z", p);
    // move last 2 digits
    if (len > 1) {
        char minute[] = { buf[len-2], buf[len-1], '\0' };
        sprintf(buf + len - 2, ":%s", minute);
    }
    return sprintf("%s", buf);
}


int getMySQLQuestionsNumber(const char* host, const char* user, const char* password, const char* database) {
    MYSQL *con = mysql_init(NULL);

    if (con == NULL) {
        fprintf(stderr, "mysql_init() failed\n");
        return -1;
    }

    if (mysql_real_connect(con, host, user, password, database, 0, NULL, 0) == NULL) {
        fprintf(stderr, "Failed to connect to mysql");
        return -1;
    }

    if (mysql_query(con, "SHOW GLOBAL STATUS LIKE 'Questions'")) {
        fprintf(stderr, "%s\n", mysql_error(con));
        mysql_close(con);
        return -1;
    }

    MYSQL_RES* result = mysql_store_result(con);
    if (result == NULL) {
        fprintf(stderr, "%s\n", mysql_error(con));
        mysql_close(con);
        return -1;
    }

    MYSQL_ROW row = mysql_fetch_row(result);
    int questions = row[0];

    mysql_free_result(result);
    mysql_close(con);

    return questions;
}
