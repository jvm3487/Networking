#include <iostream>
#include <fstream>
#include <cstdlib>
#include <map>
#include <vector>
#include <sstream>
#include <string>
#include <climits>
#include <string.h>

struct _otherData
{
  std::string nextHop;
  std::vector<std::string> asPath;
  bool bIGP;
  int MED;
  _otherData(): bIGP(false), MED(INT_MAX) {}
};
typedef struct _otherData otherData;

void parseInput(const std::string& line, std::vector<std::string>& changeIP, otherData& oneBlockData, bool& announceType)
{

  // Gather the Next Hop IP Address
  if (line.find("FROM:") != std::string::npos)
    {
      std::stringstream destIP;	  
      for (unsigned strIndex = 6; line[strIndex] != ' '; strIndex++)
	destIP << line[strIndex];
      oneBlockData.nextHop = destIP.str();
    }
      
  // Gather the origin information
  else if (line.find("ORIGIN:") != std::string::npos)
    oneBlockData.bIGP = (line.find("IGP") != std::string::npos);

  // Gather the ASPATH
  else if (line.find("ASPATH:") != std::string::npos)
    {
      for (unsigned currentIndex = 8 ; currentIndex - 1 < line.size() ; currentIndex++)
	{
	  std::stringstream oneASpath;
	  for ( ; line[currentIndex] != ' ' && line[currentIndex] != '\n'; currentIndex++)
	    oneASpath << line[currentIndex];
	  oneBlockData.asPath.push_back(oneASpath.str());
	}
    } 
  
  // Gather the MED
  else if (line.find("MULTI_EXIT_DISC:") != std::string::npos)
    {
      std::stringstream medStream;
      for (unsigned strIndex = 17; line[strIndex] != ' '; strIndex++)
	medStream << line[strIndex];
      oneBlockData.MED = std::stoi(medStream.str());
    }

  // Determine if announce
  else if (line.find("ANNOUNCE") !=std::string::npos)
    announceType = true;
  
  // Determine announced ip addresses
  else if (line[0] == ' ')
    {
      std::stringstream ipAdd;
      for (unsigned currentIndex = 2 ; currentIndex - 1 < line.size(); currentIndex++)
	ipAdd << line[currentIndex];
      changeIP.push_back(ipAdd.str());
    }
  
}

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
  // Next Hop IP Address, AS path, origin type IDP=true, and MED
  std::map<std::string, otherData > mapIPAddress;

  std::string line;
 
  while (!inputFile.eof()){
  
    std::vector<std::string> changeIP;
    otherData oneBlockData;
    bool announceType = false; //true if announce, false otherwise

    while (std::getline(inputFile, line))
      {
      	
	if (line.empty() || inputFile.eof())
	  {
	    std::cout << oneBlockData.nextHop << std::endl;
	    for (unsigned i = 0; i < oneBlockData.asPath.size(); i++)
	      std::cout << oneBlockData.asPath[i] << std::endl;
	    std::cout << oneBlockData.bIGP << std::endl;
	    std::cout << oneBlockData.MED << std::endl;
	    for (unsigned i = 0; i < changeIP.size(); i++)
	      std::cout << changeIP[i] << std::endl;
	    std::cout << announceType << std::endl;
	    break;
	  }
	else
	  parseInput(line, changeIP, oneBlockData, announceType);
      }
  }

  inputFile.close();
  return EXIT_SUCCESS;
}
