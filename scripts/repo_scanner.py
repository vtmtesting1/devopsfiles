#!/usr/bin/env python
# -*- coding: utf-8 -*-

from __future__ import absolute_import
import shutil
import sys
import math
import datetime
import argparse
import uuid
import hashlib
import tempfile
import os
import re
import json
import stat
from json2html import *
from truffleHogRegexes.regexChecks import regexes


def main():
    parser = argparse.ArgumentParser(description='Find secrets in the source code.')
    parser.add_argument('--json', dest="output_json", action="store_true", help="Output in JSON")
    parser.add_argument("--regex", dest="do_regex", action="store_true", help="Enable high signal regex checks")
    parser.add_argument("--rules", dest="rules", help="Regexes pattern from json list file")
    parser.add_argument("--entropy", dest="do_entropy", help="Enable entropy checks")
    parser.add_argument('project_path', type=str, help='Path for secret searching')
    parser.set_defaults(regex=False)
    parser.set_defaults(rules={})
    parser.set_defaults(entropy=True)
    parser.set_defaults(branch=None)
    args = parser.parse_args()
    rules = {}
    if args.rules:
        try:
            with open(args.rules, "r") as ruleFile:
                rules = json.loads(ruleFile.read())
                for rule in rules:
                    rules[rule] = re.compile(rules[rule])
        except (IOError, ValueError) as e:
            raise("Error reading rules file")
        for regex in dict(regexes):
            del regexes[regex]
        for regex in rules:
            regexes[regex] = rules[regex]
    do_entropy = str2bool(args.do_entropy)
    ignored = ['.git', 'node_modules', 'bower_components', '.sass-cache', '.gradle', '.npm', 'test', 'swagger']
    output = find_strings(args.project_path, args.output_json, args.do_regex, do_entropy, ignored, suppress_output=False)
    project_path = output["project_path"]
    # shutil.rmtree(project_path, onerror=del_rw)
    if output["foundIssues"]:
        sys.exit(1)
    else:
        sys.exit(0)

def str2bool(v):
    if v == None:
        return True
    if v.lower() in ('yes', 'true', 't', 'y', '1'):
        return True
    elif v.lower() in ('no', 'false', 'f', 'n', '0'):
        return False
    else:
        raise argparse.ArgumentTypeError('Boolean value expected.')


BASE64_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/="
HEX_CHARS = "1234567890abcdefABCDEF"

def del_rw(action, name, exc):
    os.chmod(name, stat.S_IWRITE)
    os.remove(name)

def shannon_entropy(data, iterator):
    """
    Borrowed from http://blog.dkbza.org/2007/05/scanning-data-for-entropy-anomalies.html
    """
    if not data:
        return 0
    entropy = 0
    for x in iterator:
        p_x = float(data.count(x))/len(data)
        if p_x > 0:
            entropy += - p_x*math.log(p_x, 2)
    return entropy


def get_strings_of_set(word, char_set, threshold=20):
    count = 0
    letters = ""
    strings = []
    for char in word:
        if char in char_set:
            letters += char
            count += 1
        else:
            if count > threshold:
                strings.append(letters)
            letters = ""
            count = 0
    if count > threshold:
        strings.append(letters)
    return strings

class bcolors:
    HEADER = '\033[95m'
    OKBLUE = '\033[94m'
    OKGREEN = '\033[92m'
    WARNING = '\033[93m'
    FAIL = '\033[91m'
    ENDC = '\033[0m'
    BOLD = '\033[1m'
    UNDERLINE = '\033[4m'

def print_results(printJson, issue):
    secretkeys = issue['Offending Content']
    reason = issue['Rule Description']
    path = issue['File']
    lineNumber = issue['Line Number']
    ruleNumber = issue['Rule Number']

    if printJson:
        print(json.dumps(issue, sort_keys=True))
    else:
        print("~~~~~~~~~~~~~~~~~~~~~")
        reason = "{}Reason: {}{}".format(bcolors.OKGREEN, reason, bcolors.ENDC)
        print(reason)
        filePath = "{}Filepath: {}{}".format(bcolors.OKGREEN, path, bcolors.ENDC)
        print(filePath)
        lineNumber = "{}LineNumber: {}{}".format(bcolors.OKGREEN, path, bcolors.ENDC)
        print(lineNumber)
        ruleNumber = "{}RuleNumber: {}{}".format(bcolors.OKGREEN, path, bcolors.ENDC)
        print(ruleNumber)

        if sys.version_info >= (3, 0):
            print(secretkeys)
        else:
            print(secretkeys.encode('utf-8'))
        print("~~~~~~~~~~~~~~~~~~~~~")

def find_entropy(printableDiff, fullpath):
    stringsFound = []
    number = 1
    for line in printableDiff:
        for word in line.split():
            base64_strings = get_strings_of_set(word, BASE64_CHARS)
            hex_strings = get_strings_of_set(word, HEX_CHARS)
            for string in base64_strings:
                b64Entropy = shannon_entropy(string, BASE64_CHARS)
                if b64Entropy > 4.5:
                    stringsFound.append(string)
            for string in hex_strings:
                hexEntropy = shannon_entropy(string, HEX_CHARS)
                if hexEntropy > 3:
                    stringsFound.append(string)
        number += 1
    entropicDiff = None
    if len(stringsFound) > 0:
        entropicDiff = {}
        entropicDiff['File'] = fullpath
        entropicDiff['Rule Number'] = '100'
        entropicDiff['Rule Description'] = "High Entropy"
        entropicDiff['Line Number'] = number
        entropicDiff['Offending Content'] = stringsFound
    return entropicDiff

def regex_check(printableDiff, fullpath, custom_regexes={}):
    if custom_regexes:
        secret_regexes = custom_regexes
    else:
        secret_regexes = regexes
    regex_matches = []
    number = 1
    for lines in printableDiff:
        rulenumber = 1
        for key in secret_regexes:
            found_strings = secret_regexes[key].findall(lines)
            if found_strings:
                foundRegex = {}
                foundRegex['File'] = fullpath
                foundRegex['Rule Number'] = rulenumber
                foundRegex['Rule Description'] = key
                foundRegex['Line Number'] = number
                foundRegex['Offending Content'] = found_strings
                regex_matches.append(foundRegex)
            rulenumber += 1
        number += 1
    return regex_matches

def diff_worker(diff, fullpath, custom_regexes, do_entropy, do_regex, printJson, suppress_output):
    issues = []
    printableDiff = diff
    foundIssues = []
    if do_regex:
        found_regexes = regex_check(printableDiff, fullpath, custom_regexes)
        foundIssues += found_regexes
        #foundIssues.append(found_regexes)
    if do_entropy:
        entropicDiff = find_entropy(printableDiff, fullpath)
        if entropicDiff:
            foundIssues += entropicDiff
            #foundIssues.append(entropicDiff)
    if not suppress_output:
        for foundIssue in foundIssues:
            print_results(printJson, foundIssue)
    issues += foundIssues
    return issues

def handle_results(output, output_dir, foundIssues):
    for foundIssue in foundIssues:
        result_path = os.path.join(output_dir, str(uuid.uuid4()))
        with open("repo_scan.json", "a+") as result_file:
            result_file.write(json.dumps(foundIssue))
            result_file.write("\n")
        output["foundIssues"].append(result_path)
    return output

def find_strings(project_path, printJson=False, do_regex=False, do_entropy=True, ignored=None, suppress_output=True, custom_regexes={}):
    output = {"foundIssues": []}
    already_searched = set()
    output_dir = tempfile.mkdtemp()

    for dirpath, _, filenames in os.walk(project_path):
        for name in filenames:
            if name[0] == '.':
                # ignore hidden files
                continue
            try:
                with open(os.path.join(dirpath, name), "tr") as f:
                    f.read()
            except UnicodeDecodeError:
                continue # ignore binary file
            abspath = os.path.join(dirpath, name)
            fullpath = os.path.relpath(abspath)
            skip = False
            for ignore in ignored:
                if ignore in fullpath:
                    skip = True
            if not skip:
                diff = open(fullpath, encoding="utf-8")
                foundIssues = diff_worker(diff, fullpath, custom_regexes, do_entropy, do_regex, printJson, suppress_output)
                output = handle_results(output, output_dir, foundIssues)
    data_processed = []
    with open('repo_scan.json') as f:
        for line in f:
            data_processed.append(json.loads(line))
    formatted_table = json2html.convert(json = data_processed)
    with open('repo_scan.html', 'w+') as secrets_report:
        secrets_report.write(formatted_table)
    output["project_path"] = project_path
    output["issues_path"] = output_dir
    return output

def clean_up(output):
    project_path = output.get("project_path", None)
    if project_path and os.path.isdir(project_path):
        shutil.rmtree(output["project_path"])
    issues_path = output.get("issues_path", None)
    if issues_path and os.path.isdir(issues_path):
        shutil.rmtree(output["issues_path"])

if __name__ == "__main__":
    main()

