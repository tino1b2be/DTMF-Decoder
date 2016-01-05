% Function to decode an audio file with DTMF Tones and return the sequence
% 
% Copyright (c) 2015 Tinotenda Chemvura
% 
% Permission is hereby granted, free of charge, to any person obtaining a copy
% of this software and associated documentation files (the "Software"), to deal
% in the Software without restriction, including without limitation the rights
% to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
% copies of the Software, and to permit persons to whom the Software is
% furnished to do so, subject to the following conditions:
% 
% 
% The above copyright notice and this permission notice shall be included in
% all copies or substantial portions of the Software.
% 
% 
% THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
% IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
% FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.  IN NO EVENT SHALL THE
% AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
% LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
% OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
% THE SOFTWARE.
%
% http://opensource.org/licenses/MIT
%


function out = genDTMFtestTone( DTMF_char, Fs, duration, amplitude)
%Function to generate a DTMF tone given the low and high freq and the
%sampling frequency. 
% NB: The DTMF Frequencies for the tone will be a random frequency within 
%the tolerance recommemned by the ITU-T Q.24 (<=1.5%). 
% NB: This method also inserts a random interrupt in the tone of a random 
% duration <=10ms
%The duration (in ms) of the signal is determined by the "duration" parameter. 
%The max amplitude of the signal is determined by the "amplitude" parameter.

% ITU Specifications : Max allowed frequency tolerance = 1.5%
    
    %freq_low = [697 +/-10, 770 +/-12, 852 +/-13, 941 +/- 14];
    %freq_high = [1209 +/-18, 1336 +/- 20, 1477 +/- 22, 1633 +/- 24];

%bin = {697, 770, 852, 941, 1209, 1336, 1477, 1633}

%f_bin = [687:707, 758:782, 839:865, 927:955, 1191:1227, 1316:1356, 1455:1499, 1609:1657];
%f_bin = [687:707, 758:782, 839:865, 927:955, 1191:1227, 1316:1356, 1455:1499, 1609:1657];


    if (DTMF_char == '1' || DTMF_char == 1)
        lo = randomInt(691,703);
        hi = randomInt(1201,1217);
        
    elseif (DTMF_char == '2' || DTMF_char == 2)
        lo = randomInt(691,703);
        hi = randomInt(1326,1346);
        
    elseif (DTMF_char == '3' || DTMF_char == 3)
        lo = randomInt(691,703);
        hi = randomInt(1467,1487);
        
    elseif (DTMF_char == '4' || DTMF_char == 4)
        lo = randomInt(762,778);
        hi = randomInt(1201,1217);
        
	elseif (DTMF_char == '5' || DTMF_char == 5)
        lo = randomInt(762,778);
        hi = randomInt(1326,1346);
        
	elseif (DTMF_char == '6' || DTMF_char == 6)
        lo = randomInt(762,778);
        hi = randomInt(1467,1487);
        
	elseif (DTMF_char == '7' || DTMF_char == 7)
        lo = randomInt(844,860);
        hi = randomInt(1201,1217);
        
	elseif (DTMF_char == '8' || DTMF_char == 8)
        lo = randomInt(844,860);
        hi = randomInt(1326,1346);
        
	elseif (DTMF_char == '9' || DTMF_char == 9)
        lo = randomInt(844,860);
        hi = randomInt(1467,1487);   
        
    elseif (DTMF_char == '0' || DTMF_char == 0 || DTMF_char == 10)
        lo = randomInt(933,949);
        hi = randomInt(1326,1346);
        
	elseif (DTMF_char == '*'|| DTMF_char == 11)
        lo = randomInt(933,949);
        hi = randomInt(1201,1217);
        
	elseif (DTMF_char == '#'|| DTMF_char == 12)
        lo = randomInt(933,949);
        hi = randomInt(1467,1487);
        
	elseif (DTMF_char == 'A' || DTMF_char == 'a'|| DTMF_char == 13)
        lo = randomInt(691,703);
        hi = randomInt(1622,1644);
        
	elseif (DTMF_char == 'B' || DTMF_char == 'b'|| DTMF_char == 14)
        lo = randomInt(762,778);
        hi = randomInt(1622,1644);
        
 	elseif (DTMF_char == 'C' || DTMF_char == 'c'|| DTMF_char == 15)
        lo = randomInt(844,860);
        hi = randomInt(1622,1644);
        
    elseif (DTMF_char == 'D' || DTMF_char == 'd'|| DTMF_char == 16)
        lo = randomInt(933,949);
        hi = randomInt(1622,1644);
    else
        if (isstr(DTMF_char))
            msg = strcat('"',DTMF_char,'" is not a valid DTMF character');
        else
            msg = 'That is not a valid DTMF character';
        end
        throw(MException('Invalid Character',msg));
    end

    samples = floor(duration*Fs/1000);
    t = transpose(1:samples);
    low = sin(2*pi*lo*t/Fs);
    high = sin(2*pi*hi*t/Fs);
    out = amplitude*(low+high)/2;
    
    % Generate a random normalised signal less than 10ms
    len = floor(randomInt(0,1000)/100000 * Fs);
    interr = randn(len,1);
    % clip the signal to +/-1
    for i = 1:len
        if (interr(i) >= amplitude)
            interr(i) = amplitude;
        elseif (interr(i) <= -amplitude)
            interr(i) = -amplitude;
        end
    end
    
    %insert interrupt in a random location within the signal.
    location_Index = randomInt(1,samples);
    out = vertcat(out(1:location_Index),interr,out(location_Index + 1:end-len)); % the "end-len" makes sure the signal maintains its original length
    
end
