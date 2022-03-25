#!/bin/bash
# This script handles the presence of copy right headers in all files inside the repository

set -e
MYDIR=`dirname $0`
cd $MYDIR/../..
TEMPLATE_DIRECTORY="scripts/ci/filetemplates"

FILES=`git ls-files`

EXCLUDE_DIRS="scripts/ci doc/"
EXCLUDE_PATTERNS="*.pb.go *generated.go *pb2.py *pb2_grpc.py"

if [ "$#" -ne 1 ]; then
    echo "Expected command"
    echo "ensure   Ensure copyright headers"
    echo "validate Validate copyright headers"
    echo "drop     Drop copyright headers"
    exit 1
fi

# Check if $1 is excluded
function is_excluded() {
  for EXCLUDE_DIR in $EXCLUDE_DIRS
  do
    if [[ $1 == $EXCLUDE_DIR* ]]; then
      echo "Ignoring $1 is in $EXCLUDE_DIR"
      return 0
    fi
  done
  for EXCLUDE_PATTERN in $EXCLUDE_PATTERNS
  do
    if [[ $1 == $EXCLUDE_PATTERN ]]; then
      echo "Ignoring $1 in exclude pattern $EXCLUDE_PATTERN"
      return 0
    fi
  done
  return 1
}

# Check that file $1 start with the content of file $2
function file_starts_with_file() {
  TEMPLATE_SIZE=`stat --printf="%s" $2`
  cmp -s $1 $2 -n $TEMPLATE_SIZE
}

# Sets TEMPLATE FILE for a file $1
function template_for_file() {
  BASE_FILENAME=$(basename -- "$1")
  EXTENSION="${BASE_FILENAME##*.}"
  TEMPLATE_FILE="$TEMPLATE_DIRECTORY/file_template.${EXTENSION}"
  if [ -f "$TEMPLATE_FILE" ]; then
    return 0
  else
    return 1
  fi
}

# Arguments: $1 file
function check_file() {
  if template_for_file $1; then
    # echo "Template for file $1 --> $TEMPLATE_FILE"

    if is_excluded $1 ; then
      return 0
    fi

    if file_starts_with_file $1 $TEMPLATE_FILE ; then
      # echo "Template found"
      return 0
    else
      # echo "Template not found"
      return 1
    fi
    return 0
  else
    # echo "No Template for $1"
    return 0
  fi
}

if [ "$1" = "ensure" ]; then
  echo "Ensuring..."
  for FILE in $FILES
  do
    if ! check_file $FILE ; then
      if template_for_file $FILE; then
        echo "Fixing file $FILE with $TEMPLATE_FILE"
        TEMP_FILE="$FILE.temp"
        cat $TEMPLATE_FILE $FILE > $TEMP_FILE
        cp $TEMP_FILE $FILE
        rm $TEMP_FILE
      else
        echo "Error file  $FILE has no template, but can't find one?!"
        exit 1
      fi
    fi
  done
elif [ "$1" = "validate" ]; then
  echo "Validating copyright header"
  VIOLATION_COUNT=0
  for FILE in $FILES
  do
    if ! check_file $FILE ; then
      echo "File $FILE has no valid copyright header"
      ((VIOLATION_COUNT=VIOLATION_COUNT+1))
    fi
  done
  if [ $VIOLATION_COUNT -gt 0 ]; then
    echo "Found $VIOLATION_COUNT violations"
    exit 1
  fi
  echo "Validating copyright header done, looks good"
elif [ "$1" = "drop" ]; then
  for FILE in $FILES
  do
    if ! is_excluded $FILE ; then
      if check_file $FILE ; then
        if template_for_file $FILE; then
          echo "Removing header of $FILE"
          TEMPLATE_SIZE=`stat --printf="%s" $TEMPLATE_FILE`
          TEMP_FILE="$FILE.temp"
          # Tail expects one more
          ((TEMPLATE_SIZE=TEMPLATE_SIZE+1))
          tail -c +$TEMPLATE_SIZE $FILE > $TEMP_FILE
          cp $TEMP_FILE $FILE
          rm $TEMP_FILE
        fi
      fi
    fi
  done
else
  echo "Unknown argument $1"
  exit 1
fi
