#!/bin/sh
trap 'rm -f "$0"' EXIT INT TERM
set -e
%s
