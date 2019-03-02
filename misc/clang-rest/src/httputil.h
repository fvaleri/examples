/*============================================================================

  hello-ws-c
  httputil.h
  Copyright (c)2020 Kevin Boone, GPL v3.0

============================================================================*/

#pragma once

#include <time.h>
#include "defs.h"

BEGIN_DECLS

BOOL httputil_parse_http_time(const char *buf, time_t *t);

void httputil_make_http_time(time_t t, char *buf, int buf_len);

END_DECLS
