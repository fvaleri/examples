/*==========================================================================
  hello-ws-c
  program.c
  Copyright (c)2020 Kevin Boone
  Distributed under the terms of the GPL v3.0
==========================================================================*/

#include <stdio.h>
#include <stdlib.h>
#include <unistd.h>
#include <string.h>
#include <getopt.h>
#include <wchar.h>
#include <time.h>
#include <sys/types.h>
#include <sys/wait.h>
#include <microhttpd.h>
#include <signal.h>
#include "props.h"
#include "program_context.h"
#include "program.h"
#include "request_handler.h"
#include "httputil.h"

/*============================================================================
  program_header_iterator
============================================================================*/
static int program_header_iterator(void *data, enum MHD_ValueKind kind,
                                   const char *key, const char *value)
{
    LOG_IN
    Props *headers = (Props *)data;
    props_put(headers, key, value);
    LOG_OUT
    return MHD_YES;
}

/*============================================================================
  program_argument_iterator
============================================================================*/
static int program_argument_iterator(void *data, enum MHD_ValueKind kind,
                                     const char *key, const char *value)
{
    LOG_IN
    Props *headers = (Props *)data;
    if (value) // Avoid trying to store a null header value
        props_put(headers, key, value);
    LOG_OUT
    return MHD_YES;
}

/*============================================================================
  program_handle_request
============================================================================*/
int program_handle_request(void *_request_handler,
                           struct MHD_Connection *connection, const char *url,
                           const char *method, const char *version, const char *upload_data,
                           size_t *upload_data_size, void **con_cls)
{
    LOG_IN
    int ret = MHD_YES;
    RequestHandler *request_handler = (RequestHandler *)_request_handler;

    log_debug("request: %s", url);

    Props *headers = props_create();
    MHD_get_connection_values(connection, MHD_HEADER_KIND,
                              program_header_iterator, headers);

    Props *arguments = props_create();
    MHD_get_connection_values(connection, MHD_GET_ARGUMENT_KIND,
                              program_argument_iterator, arguments);

    struct MHD_Response *response;

    int code = 200;
    char *buff;
    request_handler_api(request_handler, url, arguments, &code, &buff);

    response = MHD_create_response_from_buffer(strlen(buff),
                                               (void *)buff, MHD_RESPMEM_MUST_FREE);
    if (code == 200)
        MHD_add_response_header(response, "Content-Type",
                                "application/json; charset=utf8");
    else
        MHD_add_response_header(response, "Content-Type",
                                "text/plain; charset=utf8");

    MHD_add_response_header(response, "Cache-Control", "no-cache");
    ret = MHD_queue_response(connection, code, response);
    MHD_destroy_response(response);

    props_destroy(headers);
    props_destroy(arguments);
    LOG_OUT
    return ret;
}

/*==========================================================================
  program_run
  All the useful work starts here
==========================================================================*/
int program_run(ProgramContext *context)
{
    const char *host = program_context_get(context, "host");
    if (!host)
        host = "0.0.0.0";
    int port = program_context_get_integer(context, "port",
                                           8080);

    // the purpose of daemon() is to make the program run detached from the terminal
    // daemon is deprecated on macos (launchd should be used)
    // there is no need ever to run daemon() in a container
    #ifndef __APPLE__
        if (!program_context_get_boolean(context, "debug", FALSE))
        {
            daemon (0, 1);
        }
    #endif

    RequestHandler *request_handler = request_handler_create(context);

    sigset_t base_mask, waiting_mask;

    sigemptyset(&base_mask);
    sigaddset(&base_mask, SIGINT);
    sigaddset(&base_mask, SIGTSTP);
    sigaddset(&base_mask, SIGHUP);
    sigaddset(&base_mask, SIGQUIT);
    sigprocmask(SIG_SETMASK, &base_mask, NULL);

    log_info("HTTP server starting");

    struct MHD_Daemon *daemon = MHD_start_daemon(MHD_USE_THREAD_PER_CONNECTION, port, NULL, NULL,
                                                 program_handle_request, request_handler, MHD_OPTION_END);

    if (daemon)
    {
        while (!request_handler_shutdown_requested(request_handler))
        {
            usleep(1000000);
            sigpending(&waiting_mask);
            if (sigismember(&waiting_mask, SIGINT) ||
                sigismember(&waiting_mask, SIGTSTP) ||
                sigismember(&waiting_mask, SIGQUIT) ||
                sigismember(&waiting_mask, SIGHUP))
            {
                log_warning("Shutting down on signal");
                request_handler_request_shutdown(request_handler);
            }
        }

        log_info("HTTP server stopping");

        MHD_stop_daemon(daemon);
    }
    else
    {
        log_error("Can't start HTTP server (check port %d is not in use)",
                  port);
    }

    request_handler_destroy(request_handler);

    return 0;
}
