/*==========================================================================
  hello-ws-c
  httputil.c
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
#include <microhttpd.h>
#include "defs.h"
#include "log.h"
#include "httputil.h"

static const char *wdays[7] = {
    "Sun",
    "Mon",
    "Tue",
    "Wed",
    "Thu",
    "Fri",
    "Sat",
};

static const char *months[12] = {
    "Jan",
    "Feb",
    "Mar",
    "Apr",
    "May",
    "Jun",
    "Jul",
    "Aug",
    "Sep",
    "Oct",
    "Nov",
    "Dec",
};

/*============================================================================
  httputil_parse_http_time
============================================================================*/
BOOL httputil_parse_http_time(const char *buf, time_t *t)
{
    LOG_IN
    BOOL ret = TRUE;
    struct tm g = {0};
    char M[4];
    int i;

    sscanf(buf, "%*[a-zA-Z,] %d %3s %d %d:%d:%d",
           &g.tm_mday, M, &g.tm_year,
           &g.tm_hour, &g.tm_min, &g.tm_sec);
    for (i = 0; i < 12; i++)
    {
        if (strncmp(M, months[i], 3) == 0)
        {
            g.tm_mon = i;
            break;
        }
    }
    g.tm_year -= 1900;
    *t = timegm(&g);
    LOG_IN
    return ret;
}

/*============================================================================
  httputil_make_http_time
============================================================================*/
void httputil_make_http_time(time_t t, char *buf, int buf_len)
{
    LOG_IN
    struct tm g = {0};
    gmtime_r(&t, &g);
    snprintf(buf, buf_len,
             "%.3s, %02d %.3s %4d %02d:%02d:%02d GMT",
             wdays[g.tm_wday], g.tm_mday, months[g.tm_mon],
             g.tm_year + 1900, g.tm_hour, g.tm_min, g.tm_sec);
    LOG_OUT
}
