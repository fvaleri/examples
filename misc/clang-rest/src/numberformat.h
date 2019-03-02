/*============================================================================
  hello-ws-c
  numberformat.h
  Copyright (c)2020 Kevin Boone, GPL v3.0
============================================================================*/

#pragma once

BEGIN_DECLS

BOOL numberformat_read_integer(const char *s, uint64_t *v, BOOL strict);
BOOL numberformat_read_double(const char *s, double *v, BOOL strict);

END_DECLS
