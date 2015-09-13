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

void updateMapWithNewInfo(std::map<std::string, otherData>& mapIPAddress, const std::string& changeIPStr, const otherData& oneBlockData, const bool announceType)
{
  std::map<std::string, otherData>::iterator it;
  it = mapIPAddress.find(changeIPStr);
  
  if (announceType)
    {
      // check to see if the information should be updated or left as is
      if (it != mapIPAddress.end())
	{
	  // first check - AS path length
	  if (it->second.asPath.size() < oneBlockData.asPath.size())
	    return;
	  else if (it->second.asPath.size() == oneBlockData.asPath.size()) //same - go to next tiebreaker
	    {
	      // second check - better not IGP
	      if (it->second.bIGP == false && oneBlockData.bIGP == true)
		return;
	      else if (it->second.bIGP == oneBlockData.bIGP) // same - go to next tiebreaker
		{
		  // third check - MED
		  if (it->second.MED < oneBlockData.MED)
		    return;
		  else if (it->second.MED == oneBlockData.MED) // same - go to final tiebreak
		    {
		      unsigned currentIndexMap = 0;
		      unsigned currentIndexChallenger = 0;
		      unsigned firstTotal = 0;
		      unsigned secondTotal = 0;
		      while ( currentIndexMap - 1 < it->second.nextHop.size())
			{
			  std::stringstream ipStream;
			  std::stringstream ipStream2;
			  for ( ; it->second.nextHop[currentIndexMap] != '.' && it->second.nextHop[currentIndexMap] != ':' && currentIndexMap - 1 < it->second.nextHop.size(); currentIndexMap++)
			    ipStream << it->second.nextHop[currentIndexMap];
			  firstTotal = 0;
			  if (!ipStream.rdbuf()->in_avail())
			    firstTotal = std::stoi(ipStream.str());
			  for ( ; oneBlockData.nextHop[currentIndexChallenger] != '.' && oneBlockData.nextHop[currentIndexChallenger] != ':' && currentIndexChallenger - 1 < oneBlockData.nextHop.size(); currentIndexChallenger++)
			    ipStream2 << it->second.nextHop[currentIndexMap];
			  secondTotal = 0;
			  if (!ipStream2.rdbuf()->in_avail())
			    secondTotal = std::stoi(ipStream2.str());
			  if (firstTotal != secondTotal)
			    break;
			  currentIndexChallenger++;
			  currentIndexMap++;
			}
		      if (firstTotal <= secondTotal)
			return;
		    }
		  
		}
	    }
	  it->second = oneBlockData;
	}
      else
	{
	  mapIPAddress.emplace(changeIPStr, oneBlockData);
	}
    }
  else
    {
      if (it != mapIPAddress.end())
	mapIPAddress.erase(it);
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
	    break;
	  }
	else
	  {
	    parseInput(line, changeIP, oneBlockData, announceType);
	  }
      }

    // update the map with the changed IP address
    for (unsigned i = 0; i < changeIP.size(); i++)
      {
	updateMapWithNewInfo(mapIPAddress, changeIP[i], oneBlockData, announceType);
      }
  }

  // print out the map in the correct format
  for (const auto& mapValue : mapIPAddress)
    {
      std::cout << mapValue.first << " " << mapValue.second.nextHop;
      for (const auto& asPathValue : mapValue.second.asPath)
	{
	  std::cout << " " << asPathValue; 
	}
      std::cout << std::endl;
    } 

  inputFile.close();

  return EXIT_SUCCESS;
}
