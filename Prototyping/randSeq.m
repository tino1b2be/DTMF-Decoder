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

function [out,chars] = randSeq( numTones, Fs , amplitude)
% Funtion to generate a random sequence of DTMF tones given the number of
% tones, sampling frequency and amplitude. The duration of the tones and
% pauses is random and within the boundaries of UTI Standards.
    
    %start with a random pause
    out = genPause(randomInt(40,70),Fs);
    charsTemp = zeros(1,numTones);
    for i = 1:numTones
        % add a random tone
        DTMF = randomInt(1,16);
        charsTemp(i) = DTMF;
        tone = genDTMFtestTone(DTMF,Fs,randomInt(40,1000),amplitude);
        % add a pause of random duration between 30 and 70
        pause = genPause(randomInt(40,100),Fs);
        % add to the output signal
        out = vertcat(out,tone,pause);
    end
    
    chars = '';
    for j = 1:length(charsTemp)
        if (charsTemp(j) == 1)
            chars = strcat(chars,'1');
        elseif (charsTemp(j) == 2)
            chars = strcat(chars,'2');
        elseif (charsTemp(j) == 3)
            chars = strcat(chars,'3');
        elseif (charsTemp(j) == 4)
            chars = strcat(chars,'4');
        elseif (charsTemp(j) == 5)
            chars = strcat(chars,'5');
        elseif (charsTemp(j) == 6)
            chars = strcat(chars,'6');
        elseif (charsTemp(j) == 7)
            chars = strcat(chars,'7');
        elseif (charsTemp(j) == 8)
            chars = strcat(chars,'8');
        elseif (charsTemp(j) == 9)
            chars = strcat(chars,'9');
        elseif (charsTemp(j) == 10)
            chars = strcat(chars,'0');
        elseif (charsTemp(j) == 11)
            chars = strcat(chars,'*');
        elseif (charsTemp(j) == 12)
            chars = strcat(chars,'#');
        elseif (charsTemp(j) == 13)
            chars = strcat(chars,'A');
        elseif (charsTemp(j) == 14)
            chars = strcat(chars,'B');
        elseif (charsTemp(j) == 15)
            chars = strcat(chars,'C');
        elseif (charsTemp(j) == 16)
            chars = strcat(chars,'D'); 
        else
            chars = 'XXX';
        end
    end 
    
end % end of function

