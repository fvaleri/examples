/*==========================================================================
  hello-ws-c
  program_context.c
  Copyright (c)2020 Kevin Boone
  Distributed under the terms of the GPL v3.0
==========================================================================*/

#define _GNU_SOURCE
#include <stdio.h>
#include <stdlib.h>
#include <unistd.h>
#include <string.h>
#include <stdarg.h>
#include <errno.h>
#include <getopt.h>
#include "defs.h"
#include "log.h"
#include "props.h"
#include "program_context.h"
#include "string.h"
#include "usage.h"

struct _ProgramContext
{
    Props *props;
    int nonswitch_argc;
    char **nonswitch_argv;
};

/*==========================================================================
  program_context_create
==========================================================================*/
ProgramContext *program_context_create(void)
{
    LOG_IN
    ProgramContext *self = malloc(sizeof(ProgramContext));
    Props *props = props_create();
    self->props = props;
    props_put_integer(props, "log-level", LOG_WARNING);
    self->nonswitch_argc = 0;
    self->nonswitch_argv = NULL;
    LOG_OUT
    return self;
}

/*==========================================================================
  program_context_parse_command_line
  This needs to be called after program_context_read_rc_files, in order
  for command-line values to overwrite rc file values (if they have
  corresponding names)

  This method just process the command line, and turn the arguments into
  either context properties (program_context_put) on simply into values
  of attributes in the ProgramContext structure itself. Using properties is
  probably best, as it allows command-line arguments to override the properties
  read from RC files.

  In either case, it needs to be clear to the main program how the
  command-line arguments have been translated in context data.

  This method should only return TRUE if the intention is to proceed to
  run the rest of the program. Command-line switches that have the effect
  of terminating (like --help) should be handled internally, and
  FALSE returned.
==========================================================================*/
BOOL program_context_parse_command_line(ProgramContext *self,
                                        int argc, char **argv)
{
    BOOL ret = TRUE;
    static struct option long_options[] =
        {
            {"help", no_argument, NULL, 'h'},
            {"debug", no_argument, NULL, 'd'},
            {"version", no_argument, NULL, 'v'},
            {"log-level", required_argument, NULL, 'l'},
            {"port", required_argument, NULL, 'p'},
            {"host", required_argument, NULL, 0},
            {0, 0, 0, 0}};

    int opt;
    while (ret)
    {
        int option_index = 0;
        opt = getopt_long(argc, argv, "hvl:p:",
                          long_options, &option_index);

        if (opt == -1)
            break;

        switch (opt)
        {
        case 0:
            if (strcmp(long_options[option_index].name, "help") == 0)
                program_context_put_boolean(self, "show-usage", TRUE);
            else if (strcmp(long_options[option_index].name, "version") == 0)
                program_context_put_boolean(self, "version", TRUE);
            else if (strcmp(long_options[option_index].name, "debug") == 0)
                program_context_put_boolean(self, "debug", TRUE);
            else if (strcmp(long_options[option_index].name, "log-level") == 0)
                program_context_put_integer(self, "log-level", atoi(optarg));
            else if (strcmp(long_options[option_index].name, "port") == 0)
                program_context_put_integer(self, "port", atoi(optarg));
            else if (strcmp(long_options[option_index].name, "host") == 0)
                program_context_put(self, "host", optarg);
            else
                exit(-1);
            break;
        case 'h':
        case '?':
            program_context_put_boolean(self, "show-usage", TRUE);
            break;
        case 'd':
            program_context_put_boolean(self, "debug", TRUE);
            break;
        case 'v':
            program_context_put_boolean(self, "show-version", TRUE);
            break;
        case 'l':
            program_context_put_integer(self, "log-level",
                                        atoi(optarg));
            break;
        case 'p':
            program_context_put_integer(self, "port",
                                        atoi(optarg));
            break;
        default:
            ret = FALSE;
        }
    }

    if (ret)
    {
        self->nonswitch_argc = argc - optind + 1;
        self->nonswitch_argv = malloc(self->nonswitch_argc * sizeof(char *));
        self->nonswitch_argv[0] = (char*) strdup(argv[0]);
        int j = 1;
        for (int i = optind; i < argc; i++)
        {
            self->nonswitch_argv[j] = (char*) strdup(argv[i]);
            j++;
        }
    }

    if (program_context_get_boolean(self, "show-version", FALSE))
    {
        printf("%s: %s version %s\n", argv[0], NAME, VERSION);
        printf("Copyright (c)2020 Kevin Boone\n");
        printf("Distributed under the terms of the GPL v3.0\n");
        ret = FALSE;
    }

    if (program_context_get_boolean(self, "show-usage", FALSE))
    {
        usage_show(stdout, argv[0]);
        ret = FALSE;
    }

    return ret;
}

/*==========================================================================
  context_destroy
==========================================================================*/
void program_context_destroy(ProgramContext *self)
{
    LOG_IN
    if (self)
    {
        if (self->props)
            props_destroy(self->props);
        for (int i = 0; i < self->nonswitch_argc; i++)
            free(self->nonswitch_argv[i]);
        free(self->nonswitch_argv);
        free(self);
    }
    LOG_OUT
}

/*==========================================================================
  program_context_put
==========================================================================*/
void program_context_put(ProgramContext *self, const char *name,
                         const char *value)
{
    props_put(self->props, name, value);
}

/*==========================================================================
  program_context_get
==========================================================================*/
const char *program_context_get(const ProgramContext *self, const char *key)
{
    return props_get(self->props, key);
}

/*==========================================================================
  program_context_put_boolean
==========================================================================*/
void program_context_put_boolean(ProgramContext *self,
                                 const char *key, BOOL value)
{
    props_put_boolean(self->props, key, value);
}

/*==========================================================================
  program_context_put_integer
==========================================================================*/
void program_context_put_integer(ProgramContext *self,
                                 const char *key, int value)
{
    props_put_integer(self->props, key, value);
}

/*==========================================================================
  program_context_put_int64
==========================================================================*/
void program_context_put_int64(ProgramContext *self,
                               const char *key, int64_t value)
{
    props_put_int64(self->props, key, value);
}

/*==========================================================================
  program_context_get_boolean
==========================================================================*/
BOOL program_context_get_boolean(const ProgramContext *self,
                                 const char *key, BOOL deflt)
{
    return props_get_boolean(self->props, key, deflt);
}

/*==========================================================================
  program_context_get_integer
==========================================================================*/
int program_context_get_integer(const ProgramContext *self,
                                const char *key, int deflt)
{
    return props_get_integer(self->props, key, deflt);
}

/*==========================================================================
  program_context_get_int64
==========================================================================*/
int64_t program_context_get_int64(const ProgramContext *self,
                                  const char *key, int64_t deflt)
{
    return props_get_int64(self->props, key, deflt);
}

/*==========================================================================
  program_context_get_nonswitch_argc
==========================================================================*/
int program_context_get_nonswitch_argc(const ProgramContext *self)
{
    return self->nonswitch_argc;
}

/*==========================================================================
  program_context_get_nonswitch_argv
==========================================================================*/
char **const program_context_get_nonswitch_argv(const ProgramContext *self)
{
    return self->nonswitch_argv;
}

/*==========================================================================
  program_context_setup_logging
==========================================================================*/
void program_context_setup_logging(ProgramContext *self, LogHandler log_handler)
{
    log_set_level(program_context_get_integer(self,
                                              "log-level", LOG_WARNING));
    log_set_handler(log_handler);
}
