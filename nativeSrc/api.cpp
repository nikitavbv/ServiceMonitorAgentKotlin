#include <curl.h>

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
