#include "agent.c"

const char* makeHTTPRequest(const char* url, const char* method, const char* data);
long int getCurrentTimeMillis();
char* getCurrentTimeRFC3339();

int getMySQLQuestionsNumber(const char* host, const char* user, const char* password, const char* database);