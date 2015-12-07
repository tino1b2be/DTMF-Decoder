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

% -*- texinfo -*- 
% @deftypefn {Function File} {@var{retval} =} makeFrames (@var{input1}, @var{input2})
%
% @seealso{}
% @end deftypefn

%Author: Tinotenda Chemvura @tino1b2be
%Created: 2015-12-05

function sequence = getDTMFSequence( rawKeys )
%Function to retrieve the actual sequence of DTMF tones represented by the
%data. The input is the data from the getRawKeys() function which will be a
%string representing the DTMF character from each frames

    % go through the whole string, if the current char is not a "_",
    % add that char to the output sequence and skip to the next "_"
    % char
    
    sequence = '';
    if (rawKeys(1) ~= '_')
        sequence = rawKeys(1);
    end
    for i = 2 : length(rawKeys)
        if (rawKeys(i) == '_' && rawKeys(i-1) ~= '_')
            sequence = strcat(sequence,' ');
        elseif (rawKeys(i) ~= rawKeys(i-1))
            sequence = strcat(sequence,rawKeys(i));
        end
        
    end %end of for loop
    
end %end of function

