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
%Created: 2015-12-02

function frames = makeFrames (data, Fs)
	%This function makes frames each of size "frame size" and returns
    %a matrix with each of the columns as a seperate frame for processing
    
    %each frame size must be about 32ms long
    %frame size must be a power of 2
    
%     if (Fs > 180000)
%         frameSize = 16384; % frame size is at max 45ms long at 180000Hz ... 21ms at 384000 Hz
%     elseif (Fs > 90000)
%         frameSize = 8192;
%     elseif (Fs > 45000)
%         frameSize = 4096;
%     elseif (Fs > 23000)
%         frameSize = 2048;
%     elseif (Fs > 11500)
%         frameSize = 1024;
%     else
%         frameSize = 512;
%     end

    frameSize = 370;

    if (length(data) < frameSize)
        frameSize = length(data);
        numFrames = 1;
    else
        numFrames = floor(length(data)/frameSize)*3 - 2;
    end

    % must preallocate memory for the output
    frames = zeros(frameSize,numFrames);   % number of frames (columns)
    %frames(:,1) = data(1:frameSize);                         % slice off the first frame
    
    col = 1;
    for i= 1 : floor(frameSize/3) : length(data)
        new =  data(i:i+frameSize-1);
        frames(:,col) = new;
        if (col == numFrames) % break when all the frames have been created
            break;
        end
        col = col+1;
        
    end % end of for loop
    
end % end of function
