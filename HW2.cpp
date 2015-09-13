#include <iostream>
#include <fstream>
#include <cstdlib>
#include <map>
#include <vector>
#include <tuple>
#include <sstream>
#include <string.h>

int main (int argc, char* argv[])
{
  bool inputFailure = true;
  std::string mode;
  std::ifstream inputFile;
  if (argc < 5)
    {
      std::cerr << "Less than 5 arguments" << std::endl;
    }
  else if (strcmp(argv[1], "-m"))
    {
      std::cerr << "Expected -m as second input argument" << std::endl;
    }
  else if (strcmp(argv[2], "1") && strcmp(argv[2], "2a") && strcmp(argv[2], "2b"))
    {
      std::cerr << "Expected mode 1, 2a, or 2b" << std::endl;
    }
  else if (strcmp(argv[3], "-f"))
    {
      std::cerr << "Expected -f as fourth input argument" << std::endl;
    }
  else
    {
      inputFile.open(argv[4]);
      if (!inputFile.good())
	{
	  std::cerr << "Error reading input file" << std::endl;
	  inputFile.close();
	}
      else
	{
	  mode = argv[2];
	  inputFailure = false;
	}
    }

  if (inputFailure)
    {
      std::cerr << "Expected arguments ./HW2 -m mode{1,2a,2b} -f bgp-update-file" << std::endl;
      return EXIT_FAILURE;
    }

  // IP Address map to
  // Next Hop IP Address, AS path, origin type IDP=1, and MED
  std::map<std::string, std::tuple<std::string, std::vector<int>, bool, int> > mapIPAddress;

  std::string line;
  std::stringstream destIP;
  std::tuple<std::string, std::vector<int>, bool, int> otherInfo;
  while (std::getline(inputFile, line))
    {
      std::cout << line << std::endl;
      // Gather the Next Hop IP Address
      if (line.find("FROM:") != std::string::npos)
	{
	  for (int strIndex = 6; line[strIndex] != ' '; strIndex++)
	  destIP << line[strIndex];
	}
      if (line.empty())
	{
	  std::cout << destIP.str() << std::endl;
	  break;
	}
    }

  inputFile.close();
  return EXIT_SUCCESS;
}
