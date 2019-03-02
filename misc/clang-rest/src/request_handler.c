/*============================================================================
  hello-ws-c
  request_handler.c
  Copyright (c)2020 Kevin Boone
  Distributed under the terms of the GPL v3.0
============================================================================*/

#define _GNU_SOURCE
#include <stdio.h>
#include <stdlib.h>
#include <unistd.h>
#include <string.h>
#include <getopt.h>
#include <wchar.h>
#include <time.h>
#include <fcntl.h>
#include <ctype.h>
#include <microhttpd.h>
#include "defs.h"
#include "log.h"
#include "list.h"
#include "request_handler.h"

struct _RequestHandler
{
    BOOL shutdown_requested;
    int requests;
    int ok_requests;
    const ProgramContext *context;
};

typedef void (*APIHandlerFn)(const RequestHandler *self,
                             const List *list, char **response, int *code);

typedef struct _APIHandler
{
    const char *name;
    APIHandlerFn fn;
} APIHandler;

void request_handler_greet(const RequestHandler *self, const List *list,
                           char **response, int *code);
void request_handler_health(const RequestHandler *self, const List *list,
                            char **response, int *code);
void request_handler_metrics(const RequestHandler *self, const List *list,
                             char **response, int *code);

APIHandler handlers[] =
    {
        {"greet", request_handler_greet},
        {"health", request_handler_health},
        {"metrics", request_handler_metrics},
        {NULL, NULL}};

/*============================================================================
  request_handler_create
============================================================================*/
RequestHandler *request_handler_create(const ProgramContext *context)
{
    LOG_IN
    RequestHandler *self = malloc(sizeof(RequestHandler));
    self->shutdown_requested = FALSE;
    self->context = context;
    self->requests = 0;
    self->ok_requests = 0;
    LOG_OUT
    return self;
}

/*============================================================================
  request_handler_destroy
============================================================================*/
void request_handler_destroy(RequestHandler *self)
{
    LOG_IN
    if (self)
    {
        free(self);
    }
    LOG_OUT
}

/*============================================================================
  request_handler_greet
============================================================================*/
void request_handler_greet(const RequestHandler *self, const List *args, char **response, int *code)
{
    int argc = list_length((List *)args);
    if (argc > 1)
    {
        const char *name = list_get((List *)args, 1);
        asprintf(response, "{\"hello\": \"%s\"}\n", name);
        *code = 200;
    }
    else
    {
        asprintf(response, "/greet API had no argument\n");
        *code = 400;
    }
}

/*============================================================================
  request_handler_health
============================================================================*/
void request_handler_health(const RequestHandler *self, const List *args,
                            char **response, int *code)
{
    asprintf(response, "{\"health\": \"OK\"}\n");
    *code = 200;
}

/*============================================================================
  request_handler_metrics
============================================================================*/
void request_handler_metrics(const RequestHandler *self, const List *args,
                             char **response, int *code)
{
    asprintf(response, "{\"requests\": \"%d\"}\n"
                       "{\"requests_ok\": \"%d\"}\n"
                       "{\"requests_error\": \"%d\"}\n",
             self->requests, self->ok_requests, self->requests - self->ok_requests);
    *code = 200;
}

/*============================================================================
  request_handler_api
============================================================================*/
void request_handler_api(RequestHandler *self, const char *_uri,
                         const Props *arguments, int *code, char **page)
{
    LOG_IN
    log_debug("API request: %s", _uri);

    char *uri = (char*) strdup(_uri);
    List *args = list_create(free);
    char *sp = NULL;
    char *arg = strtok_r(uri, "/", &sp);
    while (arg)
    {
        list_append(args, (char*) strdup(arg));
        arg = strtok_r(NULL, "/", &sp);
    }

    int argc = list_length(args);
    if (argc > 0)
    {
        APIHandler *ah = &handlers[0];
        int i = 0;
        BOOL done = FALSE;
        while (ah->name && !done)
        {
            const char *arg0 = list_get(args, 0);
            if (strcmp(arg0, ah->name) == 0)
            {
                ah->fn(self, args, page, code);
                done = TRUE;
            }
            i++;
            ah = &handlers[i];
        }
        if (!done)
        {
            // Error no match
            asprintf(page, "Not found\n");
            *code = 404;
        }
    }
    else
    {
        // Error no args
        asprintf(page, "Not found\n");
        *code = 404;
    }

    LOG_OUT

    list_destroy(args);
    if (*code == 200)
        self->ok_requests++;
    self->requests++;

    free(uri);
}

/*============================================================================
  request_handler_shutdown_requested
============================================================================*/
BOOL request_handler_shutdown_requested(const RequestHandler *self)
{
    LOG_IN
    BOOL ret = self->shutdown_requested;
    LOG_OUT
    return ret;
}

/*============================================================================
  request_handler_request_shutdown
============================================================================*/
void request_handler_request_shutdown(RequestHandler *self)
{
    LOG_IN
    log_info("Shutdown requested");
    self->shutdown_requested = TRUE;
    LOG_OUT
}

/*============================================================================
  request_handler_get_program_context
============================================================================*/
const ProgramContext *request_handler_get_program_context(const RequestHandler *self)
{
    LOG_IN
    const ProgramContext *ret = self->context;
    LOG_OUT
    return ret;
}
