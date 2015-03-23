#!/usr/bin/env bash

find . -name "*.patch" -print | xargs sed -i 's/thoughtcrime\/smssecure/smssecure\/smssecure/g'
find . -name "*.patch" -print | xargs sed -i 's/org.thoughtcrime.smssecure/org.smssecure.smssecure/g'
find . -name "*.patch" -print | xargs sed -i 's/org.smssecure.smssecure.util.TextSecure/org.smssecure.smssecure.util.SMSSecure/g'
