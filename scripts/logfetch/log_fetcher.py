import argparse
import ConfigParser
import sys
import os
from termcolor import colored

from fake_section_head import FakeSectionHead
from live_logs import download_live_logs
from s3_logs import download_s3_logs
from grep import grep_files

CONF_READ_ERR_FORMAT = 'Could not load config from {0} due to {1}'
DEFAULT_CONF_FILE = os.path.expanduser('~/.logfetch')

def exit(reason):
  print colored(reason, 'red')
  sys.exit(1)

def main(args):
  check_dest(args)
  all_logs = []
  all_logs += download_s3_logs(args)
  all_logs += download_live_logs(args)
  grep_files(args, all_logs)

def check_dest(args):
  if not os.path.exists(args.dest):
    os.makedirs(args.dest)

def entrypoint():
  conf_parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter, add_help=False)
  conf_parser.add_argument("-c", "--conf_file", help="Specify config file", metavar="FILE")
  args, remaining_argv = conf_parser.parse_known_args()
  conf_file = args.conf_file if args.conf_file else DEFAULT_CONF_FILE
  config = ConfigParser.SafeConfigParser()

  defaults = { "num_parallel_fetches" : 5, "chunk_size" : 8192, "dest" : "~/.logfetch_cache", "task_count" : 1 }

  try:
    config.readfp(FakeSectionHead(open(conf_file)))
    defaults.update(dict(config.items("Defaults")))
  except Exception, err:
    print CONF_READ_ERR_FORMAT.format(conf_file, err)

  parser = argparse.ArgumentParser(parents=[conf_parser], description="Fetch log files from Singularity. One can specify either a TaskId, RequestId and DeployId, or RequestId",
          prog="log_fetcher")

  parser.set_defaults(**defaults)
  parser.add_argument("-t", "--taskId", help="TaskId of task to fetch logs for", metavar="taskId")
  parser.add_argument("-r", "--requestId", help="RequestId of request to fetch logs for", metavar="requestId")
  parser.add_argument("--task-count", help="Number of recent tasks per request to fetch logs from", metavar="taskCount")
  parser.add_argument("-d", "--deployId", help="DeployId of task to fetch logs for", metavar="deployId")
  parser.add_argument("--dest", help="Destination directory", metavar="DIR")
  parser.add_argument("-n", "--num-parallel-fetches", help="Number of fetches to make at once", type=int, metavar="INT")
  parser.add_argument("-cs", "--chunk-size", help="Chunk size for writing from response to filesystem", type=int, metavar="INT")
  parser.add_argument("-s", "--singularity-uri-base", help="The base for singularity (eg. http://localhost:8080/singularity/v1)", metavar="URI")
  parser.add_argument("-g", "--grep", help="Regex to grep for (normal grep syntax)", metavar='grep')

  args = parser.parse_args(remaining_argv)

  if args.deployId and not args.requestId:
    exit("Must specify requestId (-r) when specifying deployId")
  elif not args.requestId and not args.deployId and not args.taskId:
    exit('Must specify one of\n - taskId\n - requestId and deployId\n - requestId')

  main(args)
