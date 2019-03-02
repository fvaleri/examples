/*==========================================================================
  hello-ws-c
  numberformat.c
  Copyright (c)2020 Kevin Boone
  Distributed under the terms of the GPL v3.0
==========================================================================*/

#define _GNU_SOURCE
#include <stdio.h>
#include <stdlib.h>
#include <unistd.h>
#include <string.h>
#include <getopt.h>
#include <wchar.h>
#include <time.h>
#include <math.h>
#include "string.h"
#include "log.h"
#include "numberformat.h"

/*==========================================================================
  numberformat_read_integer

  Read a 64-bit decimal integer from a string. If strict is TRUE, then
    the entire string must consist of digits, optionally preceded by
    + or -. If FALSE, there may be
    any amount of whitespace at the start of the number, and
    any amount of anything at the end.

  This function can't read hexadecimal numbers, but (weirdly)
    numberformat_read_double can.
==========================================================================*/
BOOL numberformat_read_integer(const char *s, uint64_t *v, BOOL strict)
{
    LOG_IN
    BOOL ret = FALSE;
    if (strlen(s) == 0)
    {
        // Empty -- no way it's a number
        ret = FALSE;
    }
    else
    {
        char first = s[0];
        if ((first >= '0' && first <= '9') || first == '+' || first == '-' || !strict)
        {
            char *endp;
            uint64_t n = strtoll(s, &endp, 10);
            if (endp == s)
            {
                ret = FALSE; // No digits
            }
            else
            {
                if (*endp == 0)
                {
                    ret = TRUE;
                }
                else
                {
                    // We read _some_ digits, so this is valid except
                    //   in strict mode
                    ret = !strict;
                }
            }
            *v = n;
        }
    }
    LOG_OUT
    return ret;
}

/*==========================================================================
  numberformat_read_double

  Read a decimal floating point number from a string. If strict is TRUE, then
    the number must start at the beginning of the string, and extend to
    the end. If FALSE, there may be
    any amount of whitespace at the start of the number, and
    any amount of anything at the end.

  This function understands scientific notation ("1.2E3"). The decimal
    separator is locale-specific.

  By a weird quirk of the way strtod is implemented, this function
    will also read a hexadecimal number, if it starts with 0x.
==========================================================================*/
BOOL numberformat_read_double(const char *s, double *v, BOOL strict)
{
    LOG_IN
    BOOL ret = FALSE;
    if (strlen(s) == 0)
    {
        // Empty -- no way it's a number
        ret = FALSE;
    }
    else
    {
        char first = s[0];
        if ((first >= '0' && first <= '9') || first == '+' || first == '-' || first == '.' || first == ',' || !strict)
        {
            char *endp;
            double n = strtod(s, &endp);
            if (endp == s)
            {
                ret = FALSE; // No digits
            }
            else
            {
                if (*endp == 0)
                {
                    ret = TRUE;
                }
                else
                {
                    // We read _some_ digits, so this is valid except
                    //   in strict mode
                    ret = !strict;
                }
            }
            *v = n;
        }
    }
    LOG_OUT
    return ret;
}
