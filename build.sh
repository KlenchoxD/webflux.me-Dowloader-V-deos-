#!/usr/bin/env bash
set -e
pip install --upgrade pip
pip install "curl-cffi==0.7.3" --force-reinstall
pip install -r requirements.txt
